package org.tangowiz.link;
import org.junit.Test;
import static org.junit.Assert.*;
public class WearProtocolTest {
 @Test public void missingAdvertisementMeansLegacyOne(){assertTrue(WearProtocol.compatible(1,null));}
 @Test public void selectsCommonVersion(){assertEquals(1,WearProtocol.negotiate(new int[]{2,1}));assertTrue(WearProtocol.compatible(1,new int[]{2,1}));}
 @Test public void unsupportedVersionsFail(){assertFalse(WearProtocol.compatible(2,new int[]{2}));assertEquals(0,WearProtocol.negotiate(new int[]{2}));assertFalse(WearProtocol.compatible(0,null));}
 @Test public void inconsistentAdvertisementFails(){assertFalse(WearProtocol.compatible(1,new int[]{2}));assertFalse(WearProtocol.compatible(2,new int[]{1,2}));assertFalse(WearProtocol.compatible(1,new int[]{}));}
}
