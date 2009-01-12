(ns name.choi.joshua.fnparse.test-parse-json
  (:use clojure.contrib.test-is)
  (:require [name.choi.joshua.fnparse.json :as j]))

(deftest test-ws
  (is (= ((force j/ws) (seq "  , 55"))
         [[\space \space] (seq ", 55")])
      "parsing multiple spaces as whitespace")
  (is (= ((force j/ws) (seq "\n\r, 55"))
         [[\newline \return] (seq ", 55")])
      "parsing newline and return-char) as whitespace")
  (is (= ((force j/ws) (seq ", 55"))
         [[] (seq ", 55")])
      "parsing emptiness as whitespace"))

(deftest test-false-lit
  (is (= ((force j/false-lit) (seq "false, 55"))
         [{:kind :scalar, :content false} (seq ", 55")])
      "parsing a valid false literal"))

(deftest test-integer-lit
  (is (= ((force j/number-lit) (seq "-235, 55"))
         [{:kind :scalar, :content -235} (seq ", 55")])
      "parsing a valid integer literal")
  (is (nil? ((force j/number-lit) (seq "-a235s.5, 55")))
      "parsing an invalid integer literal fails"))

(deftest test-float-lit
  (is (= ((force j/number-lit) (seq "-235.5e2, 55"))
         [{:kind :scalar, :content -235.5e2} (seq ", 55")])
      "parsing a valid float literal"))

(deftest test-string-char
  (is (= ((force j/string-char) (seq "a, 55"))
         [\a (seq ", 55")])
      "parsing a valid alphanumeric string-character")
  (is (= ((force j/string-char) (seq "\\\\a\""))
         [\\ (seq "a\"")])
      "parsing a valid escaped backslash string-character")
  (is (= ((force j/string-char) (seq "\\na\""))
         [\newline (seq "a\"")])
      "parsing a valid escaped newline string-character")
  (is (= ((force j/string-char) [\\])
         nil)
      "parsing a lone backspace as string-character fails")
  (is (= ((force j/string-char) [\"])
         nil)
      "parsing a lone double-quote as string-character fails"))

(deftest test-decimal-digit
  (is (= ((force j/decimal-digit) [\0 \a \"])
         [\0 (list \a \")])
      "parsing a valid decimal digit"))

(deftest test-hexadecimal-digit
  (is (= ((force j/hexadecimal-digit) [\F \a \"])
         [\F (list \a \")])
      "parsing a valid letter hexadecimal digit")
  (is (= ((force j/hexadecimal-digit) [\3 \a \"])
         [\3 (list \a \")])
      "parsing an invalid numeral hexadecimal digit fails"))

(deftest test-unicode-sequence
  (is (= ((force j/string-char) [\\ \u \3 \5 \A \3 \a \"])
         [\u35A3 (list \a \")])
      "parsing a valid Unicode sequence")
  (is (= ((force j/string-char) [\\ \u \3 \5 \A \a \"]) nil)
      "parsing an invalid Unicode sequence"))

(deftest test-string-lit
  (is (= ((force j/string-lit) (seq "\"\", 55"))
         [{:kind :scalar, :content ""} (seq ", 55")])
      "parsing an empty string literal")
  (is (= ((force j/string-lit) (seq "\"ABC123DoReMi\", 55"))
         [{:kind :scalar, :content "ABC123DoReMi"} (seq ", 55")])
      "parsing an alphanumeric string literal")
  (is (= ((force j/string-lit) (seq "\"ABC\\\\123\\nDoReMi\\\"\", 55"))
         [{:kind :scalar, :content "ABC\\123\nDoReMi\""} (seq ", 55")])
      "parsing a valid string literal containing backslashed backslash, double-quote, and newline")
  (is (= ((force j/string-lit) (seq "\"ABC123\\qDoReMi\", 55")) nil)
      "parsing a string literal containing invalid escape sequence fails")
  (is (= ((force j/string-lit) (seq "\"ABC123\\u11A1DoReMi\", 55"))
         [{:kind :scalar, :content "ABC123\u11A1DoReMi"} (seq ", 55")])
      "parsing a string literal containing backslashed Unicode sequence")
  (is (= ((force j/string-lit) (seq "\"ABC123\\u111Q1DoReMi\", 55")) nil)
      "parsing an string literal containing invalid Unicode sequence fails")
  (is (= ((force j/string-lit) (seq "\"Unclosed string")) nil)
      "parsing a string literal without closing double-quote fails"))

(deftest test-begin-array
  (is (= ((force j/begin-array) (seq "[55"))
         [:begin-array (seq "55")])
      "parsing a bare begin-array) token")
  (is (= ((force j/begin-array) (seq "  \t\n[\t\r55"))
         [:begin-array (seq "55")])
      "parsing a padded begin-array) token"))

(deftest test-value
  (is (= ((force j/value) (seq "true, 5"))
         [{:kind :scalar, :content true} (seq ", 5")])
      "parsing a true literal as a value")
  (is (= ((force j/value) (seq "  [ 1, 2, 3], 5 "))
         [{:kind :array, :content [{:kind :scalar, :content 1}
                                   {:kind :scalar, :content 2}
                                   {:kind :scalar, :content 3}]}
          (seq ", 5 ")])
      "parsing a flat array as a value")
  (is (= ((force j/value) (seq "  [ 1, 2, 3, [1, 2, 3]], 5 "))
         [{:kind :array, :content [{:kind :scalar, :content 1}
                                   {:kind :scalar, :content 2}
                                   {:kind :scalar, :content 3}
                                   {:kind :array, :content [{:kind :scalar, :content 1}
                                                            {:kind :scalar, :content 2}
                                                            {:kind :scalar, :content 3}]}]}
          (seq ", 5 ")])
      "parsing a nested array as a value")
  (is (= ((force j/value) (seq "  {\"a\": 1}, 5 "))
         [{:kind :object, :content {{:kind :scalar, :content "a"}
                                   {:kind :scalar, :content 1}}}
          (seq ", 5 ")])
      "parsing a flat object as a value")
  (is (= ((force j/value) (seq "  {\"a\": 1, \"b\": {\"x\": 0}}, 5 "))
         [{:kind :object, :content {{:kind :scalar, :content "a"}
                                    {:kind :scalar, :content 1},
                                    {:kind :scalar, :content "b"}
                                    {:kind :object, :content {{:kind :scalar, :content "x"}
                                                              {:kind :scalar, :content 0}}}}}
          (seq ", 5 ")])
      "parsing a nested object as a value"))

(deftest test-array
  (is (= ((force j/array) (seq "[],"))
         [{:kind :array, :content []} (seq ",")])
      "parsing a bare empty array")
  (is (= ((force j/array) (seq " \t[ \n       ] ,"))
         [{:kind :array, :content []} (seq ",")])
      "parsing a padded empty array")
  (is (= ((force j/array) (seq " \t[ \n        ,")) nil)
      "parsing an unclosed empty array fails")
  (is (= ((force j/array) (seq " \t[  -352.1  ] ,"))
         [{:kind :array, :content [{:kind :scalar, :content -352.1}]} (seq ",")])
      "parsing a valid size-1 array")
  (is (= ((force j/array) (seq " \t[  \"String\\n\"\n ,   -352.1e3  ] ,"))
         [{:kind :array, :content [{:kind :scalar, :content "String\n"}
                                   {:kind :scalar, :content -352.1e3}]}
          (seq ",")])
      "parsing a valid size-2 array")
  (is (= ((force j/array) (seq "[\"String\\n\", [1, \"2\", null], -352.1e3],"))
         [{:kind :array, :content [{:kind :scalar, :content "String\n"}
                                   {:kind :array, :content [{:kind :scalar, :content 1}
                                                            {:kind :scalar, :content "2"}
                                                            {:kind :scalar, :content nil}]}
                                   {:kind :scalar, :content -352.1e3}]} (seq ",")])
      "parsing a valid bare size-3 nested array")
  (is (= ((force j/array) (seq " \t[  \"String\\n\"\n , [ \n 1 ,\"2\"\t,3 ] ,  -352.1e3  ] ,"))
         [{:kind :array, :content [{:kind :scalar, :content "String\n"}
                                   {:kind :array, :content [{:kind :scalar, :content 1}
                                                            {:kind :scalar, :content "2"}
                                                            {:kind :scalar, :content 3}]}
                                   {:kind :scalar, :content -352.1e3}]} (seq ",")])
      "parsing a valid padded size-3 nested array")
  (is (= ((force j/array) (seq " \t[  \"String\\n\"\n , [ \n 1 ,\"2\"\t,3 ] ,  -352.1e3  ] ,"))
         [{:kind :array, :content [{:kind :scalar, :content "String\n"}
                                   {:kind :array, :content [{:kind :scalar, :content 1}
                                                            {:kind :scalar, :content "2"}
                                                            {:kind :scalar, :content 3}]}
                                   {:kind :scalar, :content -352.1e3}]} (seq ",")])
      "parsing a valid bare size-3 array containing object"))

(deftest test-object
  (is (= ((force j/object) (seq "{},"))
         [{:kind :object, :content {}} (seq ",")])
      "parsing a bare empty object")
  (is (= ((force j/object) (seq " \t{ \n       } ,"))
         [{:kind :object, :content {}} (seq ",")])
      "parsing a padded empty object")
  (is (= ((force j/object) (seq " \t{ \n        ,")) nil)
      "parsing an unclosed empty object fails")
  (is (= ((force j/object) (seq " \t{  \"AAAA\u1111\n\" : -352.1  } ,"))
         [{:kind :object, :content {{:kind :scalar, :content "AAAA\u1111\n"},
                                    {:kind :scalar, :content -352.1}}}
          (seq ",")])
      "parsing a size-1 object")
  (is (= ((force j/object) (seq "   \t{ \"String\\n\":{\"x\": true},\"A\": -352.1e3\r},"))
         [{:kind :object, :content {{:kind :scalar, :content "String\n"}
                                    {:kind :object, :content {{:kind :scalar, :content "x"}
                                                              {:kind :scalar, :content true}}},
                                    {:kind :scalar, :content "A"}
                                    {:kind :scalar, :content -352.1e3}}}
          (seq ",")])
      "parsing a size-2 nested object")
  (is (= ((force j/object) (seq " \t{  \"String\\n\"\n :[ \n 1 ,\"2\"\t,false ], \"x\" :-352.1e3  } ,"))
         [{:kind :object, :content {{:kind :scalar, :content "String\n"}
                                    {:kind :array, :content [{:kind :scalar, :content 1}
                                                             {:kind :scalar, :content "2"}
                                                             {:kind :scalar, :content false}]},
                                    {:kind :scalar, :content "x"}
                                    {:kind :scalar, :content -352.1e3}}}
          (seq ",")])
      "parsing a padded size-3 object"))

(deftest test-load-stream
  (is (= (j/load-stream "[1, 2, 3]") [1 2 3])
      "loading a flat vector containing integers")
  (is (= (j/load-stream "[\"a\", \"b\\n\", \"\\u1234\"]")
         ["a" "b\n" "\u1234"])
      "loading a flat vector containing strings")
  (is (= (j/load-stream "{\"a\": 1, \"b\\n\": 2, \"\\u1234\": 3}")
         {"a" 1, "b\n" 2, "\u1234" 3})
      "loading a flat object containing strings and integers"))

(run-tests)
