(ns name.choi.joshua.fnparse.json
  (:refer-clojure)
  (:use name.choi.joshua.fnparse
        [clojure.contrib.seq-utils :only [flatten]]))

(defstruct node-s :kind :content)
(def make-node (partial struct node-s))
(def make-scalar-node (partial make-node :scalar))
(def make-array-node (partial make-node :array))
(def make-object-node (partial make-node :object))
(def apply-str (partial apply str))
(defn- nb-char [subrule]
  (invisi-conc subrule (update-info :column inc)))
(def nb-char-lit (comp nb-char lit))
(defn- b-char [subrule]
  (invisi-conc subrule (update-info :line inc)))

(def string-delimiter (nb-char-lit \"))
(def escape-indicator (nb-char-lit \\))
(def false-lit
  (constant-semantics (lit-conc-seq "false" nb-char-lit) (struct node-s :scalar false)))
(def true-lit
  (constant-semantics (lit-conc-seq "true" nb-char-lit) (struct node-s :scalar true)))
(def null-lit
  (constant-semantics (lit-conc-seq "null" nb-char-lit) (struct node-s :scalar nil)))
(def keyword-lit (alt false-lit true-lit null-lit))

(def space (nb-char-lit \space))
(def tab (nb-char-lit \tab))
(def newline-lit (lit \newline))
(def return-lit (lit \return))
(def line-break (b-char (rep+ (alt newline-lit return-lit))))

(def json-char (alt line-break (nb-char anything)))

(def ws (constant-semantics (rep* (alt space tab line-break)) :ws))

(def begin-array (constant-semantics (conc ws (nb-char-lit \[) ws) :begin-array))
(def end-array (constant-semantics (conc ws (nb-char-lit \]) ws) :end-array))
(def begin-object (constant-semantics (conc ws (nb-char-lit \{) ws) :begin-object))
(def end-object (constant-semantics (conc ws (nb-char-lit \}) ws) :end-object))
(def name-separator (constant-semantics (conc ws (nb-char-lit \:) ws) :name-separator))
(def value-separator (constant-semantics (conc ws (nb-char-lit \,) ws) :value-separator))

(def minus-sign (nb-char-lit \-))
(def plus-sign (nb-char-lit \+))
(def decimal-point (nb-char-lit \.))
(def exponential-sign (lit-alt-seq "eE" nb-char-lit))
(def zero-digit (nb-char-lit \0))
(def nonzero-decimal-digit (lit-alt-seq "123456789" nb-char-lit))
(def decimal-digit (alt zero-digit nonzero-decimal-digit))
(def fractional-part (conc decimal-point (rep* decimal-digit)))
(def exponential-part
  (conc exponential-sign (opt (alt plus-sign minus-sign)) (rep+ decimal-digit)))

(def number-lit
  (complex [minus (opt minus-sign)
            above-one (alt zero-digit (rep+ nonzero-decimal-digit))
            below-one (opt fractional-part)
            power (opt exponential-part)]
    (-> [minus above-one below-one power] flatten apply-str Double/parseDouble
        ((if (or below-one power) identity int)) make-scalar-node)))

(def hexadecimal-digit
  (alt decimal-digit (lit-alt-seq "ABCDEF" nb-char-lit)))

(def unescaped-char (except json-char (alt escape-indicator string-delimiter)))

(def unicode-char-sequence
  (complex [_ (nb-char-lit \u), digits (factor= 4 hexadecimal-digit)]
    (-> digits apply-str (Integer/parseInt 16) char)))

(def escaped-characters
  {\\ \\, \/ \/, \b \backspace, \f \formfeed, \n \newline, \r \return, \t \tab})

(def normal-escape-sequence
  (semantics (lit-alt-seq (keys escaped-characters) nb-char-lit) escaped-characters))

(def escape-sequence
  (complex [_ escape-indicator, character (alt normal-escape-sequence unicode-char-sequence)]
    character))

(def string-char
  (alt escape-sequence unescaped-char))

(def string-lit
  (complex [_ string-delimiter, contents (rep* string-char), _ string-delimiter]
    (struct node-s :scalar (apply str contents))))

(declare array)
(declare object)

(def value (alt string-lit number-lit keyword-lit array object))

(def additional-value
  (complex [_ value-separator, content value] content))

(def array-contents
  (complex [first-value value, rest-values (rep* additional-value)]
    (cons first-value rest-values)))

(def array
  (complex [_ begin-array, contents (opt array-contents), _ end-array]
    (struct node-s :array (vec contents))))

(def entry
  (complex [entry-key string-lit, _ name-separator, entry-val value]
    [entry-key entry-val]))

(def additional-entry
  (complex [_ value-separator, content entry]
    entry))

(def object-contents
  (complex [first-entry entry, rest-entries (rep* additional-entry)]
    (cons first-entry rest-entries)))

(def object
  (complex [_ begin-object, contents object-contents, _ end-object]
    (struct node-s :object (into {} contents))))

(def text (alt object array))

(defn parse [tokens]
  (let [[product remainder info] (text {:remainder tokens, :column 0, :line 0})]
    (println "FINISHED PARSING:" info)
    product))

(defmulti represent :kind)

(defmethod represent :object [node]
  (into {} (map #(vector (represent (key %)) (represent (val %))) (:content node))))

(defmethod represent :array [node]
  (vec (map #(represent %) (:content node))))

(defmethod represent :scalar [node]
  (:content node))

(def load-stream (comp represent parse))
