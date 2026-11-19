package org.tangowiz.app;
/** Exact-reference correction only; never infer a musical ending from live silence. */
public final class MusicalEndpoint {
 private MusicalEndpoint(){}
 public static double resolve(double indexedEnd,double measuredEnd,double measuredIndexedEnd){return Double.isFinite(measuredEnd)&&Double.isFinite(measuredIndexedEnd)
   &&measuredEnd>0&&measuredEnd<=indexedEnd&&Math.abs(measuredIndexedEnd-indexedEnd)<.001?measuredEnd:indexedEnd;}
 /** V2: the decoded audio end and the physical index slot bounds are independent. */
 public static double resolve(double indexedEnd,double measuredEnd,double measuredIndexedEnd,double decodedEnd){
  return Double.isFinite(indexedEnd)&&indexedEnd>0&&Double.isFinite(measuredEnd)&&measuredEnd>0
   &&Double.isFinite(decodedEnd)&&decodedEnd>=measuredEnd&&decodedEnd<=86400
   &&Double.isFinite(measuredIndexedEnd)&&Math.abs(measuredIndexedEnd-indexedEnd)<.001?measuredEnd:indexedEnd;
 }
}
