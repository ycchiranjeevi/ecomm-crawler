(defproject ecomm-crawler "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies 
	[[org.clojure/clojure "1.9.0"]
	 [cheshire "5.8.1"]
         [enlive "1.1.6"]]
  :plugins [[cider/cider-nrepl "0.16.0"]]
  :main ecomm-crawler.core
  :target-path "target/%s")
