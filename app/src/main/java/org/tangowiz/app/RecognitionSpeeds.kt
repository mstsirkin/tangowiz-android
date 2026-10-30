package org.tangowiz.app

/** Percent offsets; normal speed is always handled by the live recognizer. */
object RecognitionSpeeds {
 val defaults=listOf(-2.5,-1.5,-0.5,0.0,1.5,2.0)
 const val maximumCount=9
 fun validate(values:List<Double>):List<Double> {
  require(values.size in 1..maximumCount){"Choose between 1 and $maximumCount speeds"}
  require(values.all{it.isFinite()&&it in -3.0..3.0}){"Each speed must be between −3% and +3%"}
  require(values.count{it==0.0}==1){"Include normal speed (0%) exactly once"}
  require(values.map{Math.round(it*10000)}.distinct().size==values.size){"Use different values for each speed"}
  return values.sorted()
 }
 fun parse(value:String):List<Double> = validate(value.split(',').map{it.trim().toDouble()})
 fun factors(values:List<Double>)=validate(values).filter{it!=0.0}.map{1.0+it/100.0}.toDoubleArray()
 fun label(value:Double)=java.math.BigDecimal.valueOf(value).stripTrailingZeros().toPlainString()
}
