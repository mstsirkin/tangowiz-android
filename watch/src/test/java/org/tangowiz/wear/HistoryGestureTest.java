package org.tangowiz.wear;
import org.junit.Test;
import static org.junit.Assert.*;
public class HistoryGestureTest {
 @Test public void leftwardSwipeOpensHistoryFromEitherParent() {
  assertTrue(HistoryGesture.opensHistory(-100,12,60));
 }
 @Test public void rightwardBackGestureRemainsAvailableToWearOs() {
  assertFalse(HistoryGesture.opensHistory(100,12,60));
 }
 @Test public void tapsAndVerticalOrMostlyVerticalMovementDoNotOpenHistory() {
  assertFalse(HistoryGesture.opensHistory(-10,0,60));
  assertFalse(HistoryGesture.opensHistory(-80,140,60));
  assertFalse(HistoryGesture.opensHistory(0,-150,60));
 }
 @Test public void invalidCoordinatesCannotNavigate() {
  assertFalse(HistoryGesture.opensHistory(Float.NaN,0,60));
  assertFalse(HistoryGesture.opensHistory(-100,Float.NaN,60));
  assertFalse(HistoryGesture.opensHistory(-100,0,0));
 }
}
