package edu.stanford.nlp.patterns.surface;

import java.io.File;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.Set;

import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.util.FileBackedCache;

/**
 * Creates an inverted index of (word or lemma) => {file1 => {sentid1,
 * sentid2,.. }, file2 => {sentid1, sentid2, ...}}. It can be backed by
 * <code>FileBackedCache</code> if given the option (to save memory).
 * 
 * IMPORTANT: If you are using FileBackedCache, you CANNOT save the index on disk and reload it - you should create the inverted index every time! 
 * This is because Java 7 does not guarantee consistency of String.hashCode() across different JVM invocations (booooo). And FileBackedCache relies on 
 * the output of the hashCode() function.
 * 
 * @author Sonal Gupta (sonalg@stanford.edu)
 * 
 */
public class InvertedIndexByTokens {

  Map<StringwithConsistentHashCode, Hashtable<String, Set<String>>> index;
  boolean convertToLowercase;
  boolean filebacked;
  Set<String> stopWords, specialWords;

  public InvertedIndexByTokens(File invertedIndexDir, boolean lc, boolean filebacked, Set<String> stopWords, Set<String> specialWords) {
    if (filebacked)
      index = new FileBackedCache<StringwithConsistentHashCode, Hashtable<String, Set<String>>>(invertedIndexDir, 10);
    else
      // memory mapped
      index = new HashMap<StringwithConsistentHashCode, Hashtable<String, Set<String>>>();
    this.convertToLowercase = lc;
    this.stopWords = stopWords;
    if (this.stopWords == null)
      this.stopWords = new HashSet<String>();
    this.specialWords = specialWords;
  }

  void add(Map<String, List<CoreLabel>> sents, String filename, boolean indexLemma) {

    
    for (Map.Entry<String, List<CoreLabel>> sEn : sents.entrySet()) {
      for (CoreLabel l : sEn.getValue()) {
        String w = l.word();
        if (indexLemma)
          w = l.lemma();

        if (convertToLowercase)
          w = w.toLowerCase();
        StringwithConsistentHashCode ws =new StringwithConsistentHashCode(w);
        Hashtable<String, Set<String>> t = index.get(ws);
        if (t == null)
          t = new Hashtable<String, Set<String>>();
        Set<String> sentids = t.get(filename);
        if (sentids == null)
          sentids = new HashSet<String>();
        sentids.add(sEn.getKey());
        t.put(filename, sentids);
        index.put(ws, t);
      }
    }
  }

  public Map<String, Set<String>> getFileSentIds(String word) {
    return index.get(new StringwithConsistentHashCode(word));
  }

  public Map<String, Set<String>> getFileSentIds(Set<String> words) {
    Hashtable<String, Set<String>> sentids = new Hashtable<String, Set<String>>();
    for (String w : words) {
      Hashtable<String, Set<String>> st = index.get(new StringwithConsistentHashCode(w));
      if (st == null)
        throw new RuntimeException("How come the index does not have sentences for " + w);
      for (Map.Entry<String, Set<String>> en : st.entrySet()) {
        if (!sentids.containsKey(en.getKey())) {
          sentids.put(en.getKey(), new HashSet<String>());
        }
        sentids.get(en.getKey()).addAll(en.getValue());
      }
    }

    return sentids;
  }

  public Map<String, Set<String>> getFileSentIdsFromPats(Set<SurfacePattern> pats) {
    Set<String> relevantWords = new HashSet<String>();
    for (SurfacePattern p : pats) {
      Set<String> relwordsThisPat = new HashSet<String>();
      String[] next = p.getOriginalNext();
      if (next != null)
        for (String s : next) {
          s = s.trim();
          if (convertToLowercase)
            s = s.toLowerCase();
          if (!s.isEmpty())
            relwordsThisPat.add(s);
        }
      String[] prev = p.getOriginalPrev();
      if (prev != null)
        for (String s : prev) {
          s = s.trim();
          if (convertToLowercase)
            s = s.toLowerCase();
          if (!s.isEmpty())
            relwordsThisPat.add(s);
        }
      boolean nonStopW = false;
      for (String w : relwordsThisPat) {
        if (!stopWords.contains(w) && !specialWords.contains(w)) {
          relevantWords.add(w);
          nonStopW = true;
        }
      }
      // If the pat contains just the stop words, add all the stop words!
      if (!nonStopW)
        relevantWords.addAll(relwordsThisPat);

    }
    relevantWords.removeAll(specialWords);
    return getFileSentIds(relevantWords);
  }

  public Set<String> getSpecialWordsList() {
    return this.specialWords;
  }
}
