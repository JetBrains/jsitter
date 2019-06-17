(ns cljsitter
  (:import [jsitter.api Parser Language Tree Zipper NodeType]))

(defn ^Language language [x]
  (if (instance? Tree x)
    (.getLanguage ^Tree x)
    (.getLanguage ^Zipper x)))

(defn ^NodeType node-type-impl [x]
  (if (instance? Tree x)
    (.getNodeType ^Tree x)
    (.getNodeType ^Zipper x)))

(defn node-type [x]
  (let [lang (language x)
        nt (node-type-impl x)]
    (keyword (.getName lang) (.getName nt))))

(defn byte-size [x]
  (if (instance? Tree x)
    (.getByteSize ^Tree x)
    (.getByteSize ^Zipper x)))

(defn zipper [^Tree t]
  (.zipper t))

(defn byte-offset [^Zipper z]
  (.getByteOffset z))

(defn byte-range [z]
  (let [o (byte-offset z)]
    [o (+ o (byte-size z))]))

(defn up [^Zipper z]
  (.up z))

(defn down [^Zipper z]
  (.down z))

(defn right [^Zipper z]
  (.right z))

(defn left [^Zipper z]
  (.left z))

(defn next [^Zipper z]
  (.next z))

(defn skip [^Zipper z]
  (.skip z))



(comment

  (def source_file (NodeType. "source_file"))

  (def golang (let [l (jsitter.impl.ImplKt/loadTSLanguage "go")]
                (.register l source_file)
                l))

  (def parser (.parser golang source_file))


  )
