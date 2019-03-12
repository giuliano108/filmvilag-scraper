(ns scraper
  (:require [clojure.data.json :as json]
            [clojure.string :as string])
  (:import [org.jsoup Jsoup]
           [java.net URL URLEncoder]))

(def scheme-n-host "http://www.filmvilag.hu/")
(def encoding "iso-8859-2")
(def wait-msg "Waiting for search results...")

(defn show-wait-msg [] (print wait-msg) (flush))

(defn show-progress-msg [n total]
  (let [msg-lead "Fetching articles: "
        separator "/"
        msg (if (< n total)
              (str msg-lead n separator total)
              (str msg-lead "done\n"))
        ; a hack to clear the current line without managing state
        length-to-erase
        (max (count wait-msg)
             (->> (map count [msg-lead (str total) separator (str total)])
                  (reduce +)))
        erased-line (apply str (repeat length-to-erase " "))]
    (print (str "\r" erased-line "\r" msg)) (flush)))

;; (.get (Jsoup/connect url)) did not work, had to specify encoding manually.
(defn get-page [url]
  (-> (.openStream (URL. url))
      (Jsoup/parse encoding url)))

(defn get-results-page-for-query [query-string]
  (let [path-to-results "xereses_talalatok.php"
        results-per-page-key "talalatoldal"
        large-num-to-avoid-pagination 1000
        url (str scheme-n-host path-to-results "?" query-string
                 "&" results-per-page-key "=" large-num-to-avoid-pagination)]
    (get-page url)))

(defn article-ids-on-results-page [page]
  (let [rows (.getElementsByClass page "even")
        ; We can extract the article's database ID from any cell id in the row,
        ; as they all look like <article ID>_<some number>.
        article-id (fn [row]
                     (let [first-cell-id (-> row (.child 0) (.attr "id"))]
                       (-> first-cell-id (string/split #"_") first)))]
    (distinct (map article-id rows))))

(defn get-article-page-for-id [id]
  (let [path-to-article "cikk.php"
        article-id-key "cikk_id"
        url (str scheme-n-host path-to-article "?" article-id-key "=" id)]
    (get-page url)))

(defn article-page->map [page]
  (let [text-of-singleton-html-class
        (fn [class-name]
          (.text (first (.getElementsByClass page class-name))))
        [section subtitle title author lead]
        (map text-of-singleton-html-class
             ["rovat" "alcim" "cim" "szerzo" "lied"])
        date (->> (text-of-singleton-html-class "elerhetosegek_tablazat")
                  (re-find #"(\d{4})/(\d{2})")
                  rest
                  (map #(Integer/parseInt %))
                  (zipmap [:year :month]))
        paragraphs
        (->> (.getElementsByTag page "p")
             (drop-while #(not (.hasClass % "szerzo")))
             rest
             (take-while #(not (.hasClass % "elerhetosegek_tablazat")))
             (map #(.text %))
             (filter seq))
        ; A safety measure for cases when the paragraph with the `lied` (lead)
        ; class is actually empty, and the text supposed to be there can be
        ; found in the next regular paragraph instead.
        [lead paragraphs]
        (let [max-length-of-lead 250]
          ; Lead is only present together with subtitle.
          (if (and (empty? lead)
                   (seq subtitle)
                   (< (count (first paragraphs))
                      max-length-of-lead))
            [(first paragraphs) (rest paragraphs)]
            [lead paragraphs]))
        [filmographic-data paragraphs]
        ; A slightly over-engineered pattern combination to avoid
        ; any false positives but allow for some tolerance of
        ; inconsistencies/quirks/typos.
        (let [pattern-a #"(\p{Lu}(\p{Ll}| )+: [^:]+\. ){3}"
              pattern-b #"(\. Rendezte|\. \d+ perc\.$)"
              max-length 500
              filmographic-data?
              (->> paragraphs
                   (group-by #(and (some? (re-find pattern-a %))
                                   (some? (re-find pattern-b %))
                                   (< (count %) max-length))))]
          ; Sometimes more than one paragraph contains filmographic data,
          ; so leave that as a sequence too.
          [(filmographic-data? true) (filmographic-data? false)])
        required {:date date
                  :section section
                  :title title
                  :author author
                  :paragraphs paragraphs}
        might-be-missing {:subtitle subtitle
                          :lead lead
                          :filmographic-data filmographic-data}]
    (merge required
           (filter #(seq (val %)) might-be-missing))))

(def get-articles-for-query
  (let [cached-articles (atom {})]
    (fn [query-string]
      (let [results-page (do (show-wait-msg)
                             (get-results-page-for-query query-string))
            article-ids (article-ids-on-results-page results-page)
            ; These are merely for the progress message.
            id-count (count article-ids)
            indexed-ids (map vector (range 1 (inc id-count)) article-ids)]
        (->> indexed-ids
             (map (fn [[idx id]]
                    (show-progress-msg idx id-count)
                    (if-let [cached-article (@cached-articles id)]
                      cached-article
                      (let [article (-> (get-article-page-for-id id)
                                        article-page->map)]
                        (swap! cached-articles assoc id article)
                        article)))))))))

(defn get-articles [& keyvals]
  (let [query-segments (for [[query-key value] (apply hash-map keyvals)]
                         (str (name query-key) "="
                              (URLEncoder/encode (str value) encoding)))
        query-string (string/join "&" query-segments)]
    (get-articles-for-query query-string)))

(defn download-articles-to-json
  [path & keyvals]
  (->> (apply get-articles keyvals)
       (json/write-str)
       (spit path)))
