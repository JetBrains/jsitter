(ns cljsitter
  (:import [jsitter.api Parser Language Tree Zipper NodeType Terminal]))

(defn ^Language language [x]
  (if (instance? Tree x)
    (.getLanguage ^Tree x)
    (.getLanguage ^Zipper x)))

(defn ^NodeType node-type-impl [x]
  (if (instance? Tree x)
    (.getNodeType ^Tree x)
    (.getNodeType ^Zipper x)))

(defn node-type [x]
  (when x
    (let [lang (language x)
          nt (node-type-impl x)]
      (keyword (.getName lang) (.getName nt)))))

(defn byte-size [x]
  (if (instance? Tree x)
    (.getByteSize ^Tree x)
    (.getByteSize ^Zipper x)))

(defn zipper [^Tree t]
  (.zipper t))

(defn byte-offset [^Zipper z]
  (.getByteOffset z))

(defn byte-range [z]
  (when z
    (let [o (byte-offset z)]
      [o (+ o (byte-size z))])))

(defn up [^Zipper z]
  (when z
    (.up z)))

(defn down [^Zipper z]
  (when z (.down z)))

(defn right [^Zipper z]
  (when z (.right z)))

(defn left [^Zipper z]
  (when z (.left z)))

(defn next [^Zipper z]
  (when z (jsitter.api.ApiKt/next z)))

(defn skip [^Zipper z]
  (when z (jsitter.api.ApiKt/skip z)))

(defn terminal? [x]
  (when x
    (let [lang (language x)
          nt (node-type-impl x)]
      (instance? Terminal nt))))


(defn s-expr [^Zipper z]
  (let [node-type ^NodeType (.getNodeType z)]
    (if (instance? Terminal node-type)
      (.getName node-type)
      (lazy-seq (let [child (.down z)]
                  (apply list (symbol (.getName node-type))
                         (map s-expr (take-while some? (iterate #(.right ^Zipper %) child)))))))))

(comment

  (def source_file (NodeType. "source_file"))

  (def golang (let [l (jsitter.impl.ImplKt/loadTSLanguage "go")]
                (.register l source_file)
                l))

  (def parser (.parser golang source_file))


  )
