/* Created on Dec 24, 2012 by Florian Leitner.
 * Copyright 2012. All rights reserved. */
package txtfnnl.uima.pattern;

import txtfnnl.pattern.Transition;
import txtfnnl.uima.tcas.TokenAnnotation;

/**
 * LambdaTokenTransition TokenAnnotation element matcher for a pattern matching implementation.
 * This is a default implementation that matches any token.
 * 
 * @author Florian Leitner
 */
public class TokenLambdaTransition implements Transition<TokenAnnotation> {
  public TokenLambdaTransition() {
    super();
  }

  /** This transition always matches. */
  public boolean matches(TokenAnnotation target) {
    return true;
  }

  @Override
  public boolean equals(Object o) {
    if (o == this) return true;
    else if (!(o instanceof TokenLambdaTransition)) return false;
    else return true; // XXX: all instances are the same (so far...)
    //else return ((TokenLambdaTransition) o).hashCode() == hashCode();
  }

  @Override
  public int hashCode() {
    int result = 17;
    // XXX: all instances the same (so far...)
    return result;
  }

  @Override
  public String toString() {
    return "*";
  }

  public double weight() {
    return Double.MIN_VALUE; // slightly better than a simple epsilon transition
  }
}
