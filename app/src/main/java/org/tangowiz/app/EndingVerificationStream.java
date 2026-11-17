package org.tangowiz.app;
/** The verifier must use the stream that actually identified the music. */
public final class EndingVerificationStream {
 public enum Stream { NORMAL, ADAPTIVE, UNAVAILABLE }
 private EndingVerificationStream(){}
 public static Stream select(double matchedSpeed,double adaptiveSpeed,boolean adaptiveReady){if(!Double.isFinite(matchedSpeed)||matchedSpeed<.5||matchedSpeed>2)return Stream.UNAVAILABLE;
  if(Math.abs(matchedSpeed-1)<.0001)return Stream.NORMAL;
  return adaptiveReady&&Double.isFinite(adaptiveSpeed)&&Math.abs(matchedSpeed-adaptiveSpeed)<.0001?Stream.ADAPTIVE:Stream.UNAVAILABLE;}
}
