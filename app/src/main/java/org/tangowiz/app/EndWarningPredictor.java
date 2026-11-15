package org.tangowiz.app;
/** Ending warning evidence is distinct from the destructive matcher-reset decision. */
public final class EndWarningPredictor {
 private final SongEndPredictor predictor=new SongEndPredictor(7,0);
 private double last=Double.NEGATIVE_INFINITY;
 public void clear(){predictor.clear();last=Double.NEGATIVE_INFINITY;}
 public void observe(double query,String id,double score,int confidence,boolean unambiguous,double reference,double end,int frames,double speed){
  if(confidence<70||!unambiguous||!Double.isFinite(query)||query<=last)return;
  last=query;predictor.observe(query,id,score,reference,end,frames,speed);
 }
 public SongEndPredictor.Prediction prediction(){return predictor.prediction();}
}
