(ns ecomm-crawler.core
  (:require [ecomm-crawler.crawler :as crawler])
  (:gen-class))

(defn -main
  "This program tries to get the complete list of urls.
   As of now it gets urls crawled in first ten seconds,
   and links present in the web pages crawled in first ten seconds.

  TODO: 1. Crawl until the all the links in the url-queue, and
           crawl all the links in those links, recursively
        2. Get the list of Products in the links crawled
        3. Get the list of Categories in the links crawled."
  [& args]
  (cond
    (zero? (count args)) (println "Please enter a url to crawl")
    (> (count args) 1) (println "Please enter a valid url to crawl")
    :else  (let [[crawled-urls url-queue]
                 (crawler/crawl-the-site 25 (str args))]
             (into (vec (seq crawled-urls)) (vec (.toArray url-queue))))))
