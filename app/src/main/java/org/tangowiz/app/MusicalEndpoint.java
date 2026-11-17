package org.tangowiz.app;
/** Exact-reference correction only; never infer a musical ending from live silence. */
public final class MusicalEndpoint {
 private MusicalEndpoint(){}
 public static double resolve(double indexedEnd,double measuredEnd,double measuredIndexedEnd){return Double.isFinite(measuredEnd)&&Double.isFinite(measuredIndexedEnd)
   &&measuredEnd>0&&measuredEnd<=indexedEnd&&Math.abs(measuredIndexedEnd-indexedEnd)<.001?measuredEnd:indexedEnd;}
}
