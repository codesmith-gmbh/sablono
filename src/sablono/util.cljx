(ns sablono.util
  #+cljs (:import goog.Uri)
  (:refer-clojure :exclude [replace])
  (:require [clojure.set :refer [rename-keys]]
            [clojure.string :refer [blank? capitalize join split replace]]))

(def ^:dynamic *base-url* nil)

(defprotocol ToString
  (to-str [x] "Convert a value into a string."))

(defprotocol ToURI
  (to-uri [x] "Convert a value into a URI."))

(defn as-str
  "Converts its arguments into a string using to-str."
  [& xs]
  (apply str (map to-str xs)))

(defn camel-case
  "Returns camel case version of the key, e.g. :http-equiv becomes :httpEquiv."
  [k]
  (if k
    (let [[first-word & words] (split (name k) #"-")]
      (if (or (empty? words)
              (= "aria" first-word)
              (= "data" first-word))
        k (-> (map capitalize words)
              (conj first-word)
              join
              keyword)))))

(defn html-to-dom-attrs
  "Converts all HTML attributes to their DOM equivalents."
  [attrs]
  (let [dom-attrs (merge (zipmap (keys attrs) (map camel-case (keys attrs)))
                         {:class :className :for :htmlFor})]
    (rename-keys attrs dom-attrs)))

(defn compact-map
  "Removes all map entries where the value of the entry is empty."
  [m]
  (reduce
   (fn [m k]
     (let [v (get m k)]
       (if (empty? v)
         (dissoc m k) m)))
   m (keys m)))

(defn merge-with-class
  "Like clojure.core/merge but concatenate :class entries."
  [& maps]
  (let [classes (->> (mapcat #(cond
                               (list? %1) [%1]
                               (sequential? %1) %1
                               :else [%1])
                             (map :class maps))
                     (remove nil?) vec)
        maps (apply merge maps)]
    (if (empty? classes)
      maps (assoc maps :class classes))))

(defn strip-css
  "Strip the # and . characters from the beginning of `s`."
  [s] (if s (replace s #"^[.#]" "")))

(defn match-tag
  "Match `s` as a CSS tag and return a vector of tag name, CSS id and
  CSS classes."
  [s]
  (let [[tag-name & matches] (re-seq #"[#.]?[^#.]+" (name s))]
    (if tag-name
      [tag-name
       (first (map strip-css (filter #(= \# (first %1)) matches)))
       (vec (map strip-css (filter #(= \. (first %1)) matches)))]
      (throw (ex-info (str "Can't match CSS tag: " s) {:tag s})))))

(defn normalize-element
  "Ensure an element vector is of the form [tag-name attrs content]."
  [[tag & content]]
  (when (not (or (keyword? tag) (symbol? tag) (string? tag)))
    (throw (ex-info (str tag " is not a valid element name.") {:tag tag :content content})))
  (let [[tag id class] (match-tag tag)
        tag-attrs (compact-map {:id id :class class})
        map-attrs (first content)]
    (if (map? map-attrs)
      [tag (merge-with-class tag-attrs map-attrs) (next content)]
      [tag tag-attrs content])))

(defn join-classes
  "Join the `classes` with a whitespace."
  [classes]
  (join " " (flatten classes)))

(defn react-symbol
  "Returns the React function to render `tag` as a symbol."
  [tag] (symbol "js" (str "React.DOM." (name tag))))

(defn react-fn
  "Same as `react-symbol` but wrap input and text elements."
  [tag]
  (let [dom-fn (react-symbol tag)]
    (if (contains? #{:input :textarea} (keyword tag))
      (symbol "sablono.interpreter" (name tag))
      dom-fn)))

(defn attr-pattern
  "Returns a regular expression that matches the HTML attribute `attr`
  and it's value."
  [attr]
  (re-pattern (str "\\s+" (name attr) "\\s*=\\s*['\"][^\"']+['\"]")))

(defn strip-attr
  "Strip the HTML attribute `attr` and it's value from the string `s`."
  [s attr]
  (if s (replace s (attr-pattern attr) "")))

(defn strip-outer
  "Strip the outer HTML tag from the string `s`."
  [s]
  (if s (-> (replace s #"^\s*<[^>]+>\s*" "")
            (replace #"\s*</[^>]+>\s*$" ""))))

#+cljs
(extend-protocol ToString
  cljs.core.Keyword
  (to-str [x]
    (name x))
  goog.Uri
  (to-str [x]
    (if (or (. x (hasDomain))
            (nil? (. x (getPath)))
            (not (re-matches #"^/.*" (. x (getPath)))))
      (str x)
      (let [base (str *base-url*)]
        (if (re-matches #".*/$" base)
          (str (subs base 0 (dec (count base))) x)
          (str base x)))))
  nil
  (to-str [_]
    "")
  number
  (to-str [x]
    (str x))
  default
  (to-str [x]
    (str x)))

#+cljs
(extend-protocol ToURI
  Uri
  (to-uri [x] x)
  default
  (to-uri [x] (Uri. (str x))))
