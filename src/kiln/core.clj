(ns kiln.core
  (:gen-class)
  (:use stencil.core)
	(:import [java.net URLEncoder])
  (:require [clojure.data.json :as json]
			[markdown.core :as markdown]
			[clj-time.coerce :as timec]
			[clj-time.format :as timef]
      [clj-rss.core :as rss]
			))

(def config (read-string (slurp "./config.clj")))

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

(defn- generation
	"生成 html 文件"
	[md-file]
	(with-open [rdr (clojure.java.io/reader (.getPath md-file))]
		(let [lines (line-seq rdr)
			  meta-lines (take-while #(re-matches #"^[\w\s]+:(.+)$" %) lines)
			  md-string (clojure.string/join "\n" (drop (count meta-lines) lines))
			  meta-dict
					(merge {:content (markdown/md-to-html-string md-string)
							:date (timef/unparse (timef/formatter "yyyy-MM-dd") (timec/from-long (.lastModified md-file)))
							}
						(into {} (for
							 [[_ k v] (map #(re-matches #"^([\w]+)[\s]*:[\s]*(.+)$" %) meta-lines)]
							 [(keyword (clojure.string/lower-case k)) (if (= k "tags") (set (map clojure.string/trim (clojure.string/split v #","))) v)])))
				id (clojure.string/replace (.getName md-file) #".md$" "")
			  html-file (str "/article/" id ".html")]
			(spit (str (:output config) html-file)
				(render-string
          (load-template "article")
          (merge
            config
            meta-dict
            {:tags (map #(hash-map :name %1, :urlcode (URLEncoder/encode %1 "UTF-8")) (get meta-dict :tags #{}))})
					))
			(let [data-clj (str (:output config) "/.data.clj")
				  data-list
					(assoc (if (.isFile (clojure.java.io/file data-clj))
							(read-string (slurp data-clj))
							{})
						id
						(select-keys meta-dict [:title :date :tags])
						;{:title (:title meta-dict) :date (:date meta-dict) :tags (get meta-dict :tags [])}
												 )]
				(spit data-clj (pr-str data-list)))
			(println html-file)
			id
			)))

(defn- spit-index
	"把有改动部分文章更新到索引文件中，包括index.html和/tags/*.html"
	[id-list]
  (if (not (empty? id-list))
	(let [article-list (read-string (slurp (str (:output config) "/.data.clj")))
				all-tags (reduce #(clojure.set/union %1 %2) #{} (map #(get %1 :tags #{}) (vals article-list)))
				last-tags (reduce #(clojure.set/union %1 %2) #{} (map #(get %1 :tags #{}) (vals (select-keys article-list id-list))))]
		;更新首页
    (println "/index.html")
		(spit (str (:output config) "/index.html")
			(render-string
        (load-template "index")
				(merge config {:tags (map #(hash-map :name %1, :urlcode (URLEncoder/encode %1 "UTF-8")) all-tags)})))
    ;rss
    (let [sort-article-list (take 10 (reverse (sort-by :date (map #(assoc (last %1) :urlcode (URLEncoder/encode (first %1) "UTF-8") ) article-list))))]
      (println "/rss.xml")
			(spit (str (:output config) "/rss.xml")
            (rss/channel-xml {:title (get config :blog-name)
                              :link (get config :blog-url)
                              :description (get config :blog-description)
                              :language (get config :blog-language "zh-CN")}
                         (map #(hash-map
                                 :title (get %1 :title "None")
                                 :link (str "/article/" (:urlcode %1) ".html")
                                  ) sort-article-list))))
		;更新tag页
		(doseq [tag last-tags]
      (println (str "/tag/" tag ".html"))
			(spit (str (:output config) "/tag/" tag ".html")
				(render-string
          (load-template "tag")
					(merge config
						{:article-list
							(reverse (sort-by :date (filter
								#(contains? (get %1 :tags #{}) tag)
								(map #(merge (last %1) {:id (first %1) :urlid (URLEncoder/encode (first %1) "UTF-8")}) article-list))))
						 :title tag})))))))

(defn -main
  "I don't do a whole lot ... yet."
  [& args]
	(.mkdirs (java.io.File. (str (:output config) "/article/")))
	(.mkdirs (java.io.File. (str (:output config) "/tag/")))
  (if (contains? config :template) (.mkdirs (java.io.File. (:template config))))
	(if (not (.isFile (clojure.java.io/file (str (:output config) "/.data.clj"))))
		(spit (str (:output config) "/.data.clj") "{}"))
	(spit-index
		(map
			generation
			(-> (:input config)
					clojure.java.io/file
					file-seq
					only-markdown
					only-update)))
  (println "目标文件已生成"))
