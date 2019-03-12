(defproject filmvilag-scraper "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Unlicense"
            :url "http://unlicense.org/UNLICENSE"}
  :dependencies [[org.clojure/clojure "1.10.0"]
                 [org.clojure/data.json "0.2.6"]
                 [org.jsoup/jsoup "1.11.3"]]
  :main ^:skip-aot filmvilag-scraper.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}})
