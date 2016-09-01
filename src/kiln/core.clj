(ns kiln.core
  (:gen-class)
  (:use stencil.core)
  (:import [java.net URLEncoder])
  (:require [clojure.data.json :as json]
      [markdown.core :as markdown]
      [clj-time.core :as timet]
      [clj-time.coerce :as timec]
      [clj-time.format :as timef]
      [clj-rss.core :as rss]
      ))

(def config (read-string (slurp "./_config.clj")))

(def all-article (atom 
  (let [data-clj (str (:output config) "/.data.clj")]
    (if (.isFile (clojure.java.io/file data-clj))
      (read-string (slurp data-clj))
      {}))
))

(defn- load-template
  "加载模板文件"
  [tid]
  (let [template-file (clojure.java.io/file (str (:template config) "/" tid ".mustache"))
        resource-file (clojure.java.io/resource (str "templates/" tid ".mustache"))]
    (if (and (contains? config :template) (.isFile template-file))
      (slurp template-file)
      (or (slurp resource-file) "not found template-file"))))

(defn- only-markdown
  " 过滤出目录中的 markdown 文件"
  [file-s]
  (filter #(and (.isFile %) (re-find #".md$" (.getName %))) file-s))

(defn- only-update
  "过滤出需要更新的markdown文件"
  [file-s]
  (filter
    (fn [f]
      (let [html-file (clojure.java.io/file (str (:output config) "/article/" (clojure.string/replace (.getName f) #".md$" "") ".html"))]
       (or (not (.isFile html-file)) (< (.lastModified html-file) (.lastModified f)))))
    file-s))

(def blogdate-formatter
  (timef/formatter
    (timet/default-time-zone) 
    "YYYY-MM-dd HH:mm:ss"
    "YYYY-MM-dd"
    "YYYY/MM/dd"
    "YYYY-MM-dd HH"
    "YYYY-MM-dd HH:mm"
    ))
(defn- read-markdown
  "读取文章内容"
  [md-file]
  (with-open [rdr (clojure.java.io/reader (.getPath md-file))]
    (let [lines (line-seq rdr)
        meta-lines (take-while #(re-matches #"^[\w\s]+:(.+)$" %) lines)
        md-string (clojure.string/join "\n" (drop (count meta-lines) lines))
        html-content (markdown/md-to-html-string md-string)
        meta-dict
          (merge
           {:date (timec/from-long (.lastModified md-file))}
           (into
             {}
             (for [[_ k v] (map #(re-matches #"^[\s]*([\w]+)[\s]*:[\s]*(.+)[\s]*$" %) meta-lines)]
               [
                (keyword (clojure.string/lower-case (clojure.string/trim k)))
                (cond
                  (= k "tags") (set (filter #(not (clojure.string/blank? %1)) (map clojure.string/trim (clojure.string/split v #","))))
                  (= k "date") (timec/to-long (timef/parse blogdate-formatter (clojure.string/trim v)))
                  :else v
                  )
                ]))) ]
         {
          :meta-dict meta-dict
          :md-string md-string
          :html-content html-content
         }
         )))
(defn- generation
  "生成 html 文件"
  [md-file]
  (let [{meta-dict :meta-dict md-string :md-string html-content :html-content} (read-markdown md-file)
        id (clojure.string/replace (.getName md-file) #".md$" "")
        html-file (str "/article/" id ".html")]
      (spit (str (:output config) html-file)
        (render-string
          (load-template "article")
          (merge
            config
            meta-dict
            {:tags (map #(hash-map :name %1, :urlcode (URLEncoder/encode %1 "UTF-8")) (get meta-dict :tags #{}))
             :date (timef/unparse
                    (timef/formatter (timet/default-time-zone) "YYYY-MM-dd" "YYYY/MM/dd")
                    (timec/from-long (:date meta-dict)))
             :content html-content })
          ))
      (swap! all-article assoc id (select-keys meta-dict [:title :date :tags]))
      id
      ))

(defn- spit-index
  "把有改动部分文章更新到索引文件中，包括index.html和/tags/*.html"
  [id-list]
  (if (not (empty? id-list))
  (let [last-tags (reduce #(clojure.set/union %1 %2) #{} (map #(get %1 :tags #{}) (vals (select-keys @all-article id-list))))
        all-articles (reverse (sort-by :date (map #(assoc (last %1) :urlcode (URLEncoder/encode (str (first %1)) "UTF-8") :id (first %1)) @all-article)))
        all-tags (frequencies (apply concat (map :tags #_(get %1 :tags #{}) (vals @all-article))))
        max-weight (apply max (conj (vals all-tags) 10))]
    ;更新首页
    (println "->" "/index.html")
    (spit (str (:output config) "/index.html")
      (render-string
        (load-template "index")
        (merge 
          config 
          {:tags (map
                   #(hash-map
                      :name (first %1), 
                      :urlcode (URLEncoder/encode (str (first %1)) "UTF-8")
                      :weight (format "%.1f" (float (+ 1 (* (/ (last %1) max-weight) 5))))
                      ) all-tags)
           :articles (take 20 all-articles)})))
    ;rss
    (let [sort-article-list (take 10 all-articles)]
      (println "->" "/rss.xml")
      (spit (str (:output config) "/rss.xml")
            (rss/channel-xml
              {
               :title (get config :blog-name)
               :link (get config :blog-url)
               :description (get config :blog-description)
               :language (get config :blog-language "zh-CN")
               :lastBuildDate (timec/to-date (timet/now))
              }
              (map #(hash-map
                      :title (str "<![CDATA[ " (get %1 :title "None") " ]]>")
                      :pubDate (timec/to-date (timec/from-long (:date %1)))
                      :guid (str (:blog-url config) "/article/" (:urlcode %1) ".html")
                      :link (str (:blog-url config) "/article/" (:urlcode %1) ".html")
                      :description (str "<![CDATA[ " (:html-content (read-markdown (clojure.java.io/file (str (:input config) "/" (:id %1) ".md")))) " ]]>"))
                   sort-article-list))))
    ;更新tag页
    (doseq [tag last-tags]
      (println "->" (str "/tag/" tag ".html"))
      (spit (str (:output config) "/tag/" tag ".html")
        (render-string
          (load-template "tag")
          (merge config
            {:article-list
              (reverse (sort-by :date (filter
                #(contains? (get %1 :tags #{}) tag)
                (map #(merge (last %1)
                        {:id (first %1)
                         :urlid (URLEncoder/encode (first %1) "UTF-8")
                         :date (timef/unparse
                                (timef/formatter (timet/default-time-zone) "YYYY-MM-dd" "YYYY/MM/dd")
                                (timec/from-long (:date (last %1))))
                         }) @all-article))))
             :title tag})))))))

(defn -main
  "I don't do a whole lot ... yet."
  [& args]
  (.mkdirs (java.io.File. (str (:output config) "/article/")))
  (.mkdirs (java.io.File. (str (:output config) "/tag/")))
  (if (contains? config :template) (.mkdirs (java.io.File. (:template config))))
  (spit-index
    (mapv
      #(do
         (println "->" (.getName %1))
         (generation %1))
      (-> (:input config)
          clojure.java.io/file
          file-seq
          only-markdown
          only-update)))
  (spit (str (:output config) "/.data.clj") (pr-str @all-article))
  (println "done"))
