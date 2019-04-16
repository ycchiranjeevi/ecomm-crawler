(ns ecomm-crawler.crawler
  (:require [net.cgrand.enlive-html :as enlive])
  (:use [clojure.string :only (lower-case)]
        [clojure.java.io :only (as-url)])
  (:import (java.net URL MalformedURLException)
           [java.util.concurrent BlockingQueue LinkedBlockingQueue]))

(defn- links-from [base-url html]
  (remove nil? (for [link (enlive/select html [:a])]
                 (when-let [href (-> link :attrs :href)]
                   (try
                     (URL. base-url href)
                     (catch MalformedURLException e))))))

(defn- words-from [html]
  (let [chunks (-> html
                 (enlive/at [:script] nil)
                 (enlive/select [:body enlive/text-node]))]
    (->> chunks
      (mapcat (partial re-seq #"\w+"))
      (remove (partial re-matches #"\d+"))
      (map lower-case))))

(def url-queue (LinkedBlockingQueue.))  ; Java thread-safe queue
(def crawled-urls (atom #{}))  ; set
(def word-freqs (atom {}))  ; map

(declare get-url)

(def agents (set (repeatedly 25 #(agent {::t #'get-url :queue url-queue}))))

(declare run process handle-results)

;; Below, our three agent actions: get-url, process,
;; handle-results.  Note common structure using try.
;;
;; Note below that *agent* is implicitly bound by Clojure to the
;; current agent; calling run on it queues the next transition,
;; based on ::t state.
(defn ^::blocking get-url [{:keys [^BlockingQueue queue] :as state}]
  (let [url (as-url (.take queue))]
    (try
      (if (@crawled-urls url)
        state  ; Already crawled, no change.
        {:url url  ; Otherwise, process new URL
         :content (slurp url)
         ::t #'process})
      (catch Exception e state)  ; Skip any failing URL.
      (finally (run *agent*)))))

(defn process [{:keys [url content]}]
  (try
    (let [html (enlive/html-resource (java.io.StringReader. content))]
      {::t #'handle-results
       :url url
       :links (links-from url html)
       :words (reduce (fn [m word]
                        update-in m [word] (fnil inc 0))
                      {}
                      (words-from html))})
    (finally (run *agent*))))

(defn ^::blocking handle-results [{:keys [url links words]}]
  ;; Update our three key states: crawled-urls, url-queue,
  ;; word-freqs
  (try
    (swap! crawled-urls conj url)
    (doseq [url links]
      (.put url-queue url))
    (swap! word-freqs (partial merge-with +) words)
    {::t #'get-url :queue url-queue}
    (finally (run *agent*))))

(defn paused? [agent] (::paused (meta agent)))

(defn run
  ;; Two signatures, 0 and 1 arities:
  ([] (doseq [a agents] (run a)))
  ([a]
    (when (agents a)
      (send a (fn [{transition ::t :as state}]
                (when-not (paused? *agent*)
                  (let [dispatch-fn (if (-> transition meta ::blocking)
                                      send-off
                                      send)]
                    (dispatch-fn *agent* transition)))
                state)))))

;; Pause or restart just from using metadata:
(defn pause
  ([] (doseq [a agents] (pause a)))
  ([a] (alter-meta! a assoc ::paused true)))

(defn restart
  ([] (doseq [a agents] (restart a)))
  ([a]
    (alter-meta! a dissoc ::paused)
    (run a)))

(defn crawl-the-site
  "Resets all state associated with the crawler, adds the given URL to the
   url-queue, and runs the crawler for 60 seconds, returning a vector
   containing the number of URLs crawled, and the number of URLs
   accumulated through crawling that have yet to be visited."
  [agent-count starting-url]
  (def agents (set (repeatedly agent-count
                     #(agent {::t #'get-url :queue url-queue}))))
  (.clear url-queue)
  (swap! crawled-urls empty)
  (swap! word-freqs empty)
  (.add url-queue starting-url)
  (run)
  ; To get initial list
  (Thread/sleep 10000)
  (pause)
  [@crawled-urls url-queue])
