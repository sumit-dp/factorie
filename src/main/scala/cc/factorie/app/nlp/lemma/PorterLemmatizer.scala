/* Copyright (C) 2008-2016 University of Massachusetts Amherst.
   This file is part of "FACTORIE" (Factor graphs, Imperative, Extensible)
   http://factorie.cs.umass.edu, http://github.com/factorie
   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at
    http://www.apache.org/licenses/LICENSE-2.0
   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */
package cc.factorie.app.nlp.lemma
import cc.factorie.app.nlp._

class PorterLemmatizer extends DocumentAnnotator with Lemmatizer {
  def lemmatize(word:String): String = cc.factorie.app.strings.PorterStemmer(word)
  def process(document:Document): Document = {
    for (token <- document.tokens) token.attr += new PorterTokenLemma(token, lemmatize(token.string))
    document
  }
  override def tokenAnnotationString(token:Token): String = { val l = token.attr[PorterTokenLemma]; l.value }
  def prereqAttrs: Iterable[Class[_]] = List(classOf[Token])
  def postAttrs: Iterable[Class[_]] = List(classOf[PorterTokenLemma])
}
object PorterLemmatizer extends PorterLemmatizer

class PorterTokenLemma(token:Token, s:String) extends TokenLemma(token, s)
