package com.monkaydee.tcgcatalogue.grade

import com.monkaydee.tcgcatalogue.scan.Pixels
import kotlin.math.abs
import kotlin.math.sqrt

/** Compares corner silhouettes only when the printed border provides uniform contrast. */
object ContourCheck {
    enum class Evidence { COMPARABLE, ASYMMETRIC, INCONCLUSIVE }
    fun measure(card: Pixels): Map<String, Evidence> {
        val span = (card.width * .07).toInt()
        if (span < 12) return Wear.CORNERS.associateWith { Evidence.INCONCLUSIVE }
        fun rgb(x: Int, y: Int): DoubleArray {
            val c=card.argb[y*card.width+x]
            return doubleArrayOf(((c shr 16) and 255).toDouble(),((c shr 8) and 255).toDouble(),(c and 255).toDouble())
        }
        fun dist(a: DoubleArray,b: DoubleArray)=sqrt(a.indices.sumOf { (a[it]-b[it])*(a[it]-b[it]) })
        val profiles=Wear.CORNERS.associateWith { name ->
            fun colour(x:Int,y:Int)=rgb(if(name.endsWith("L")) x else card.width-1-x,if(name.startsWith("T")) y else card.height-1-y)
            val samples=(span/2 until span step 3).map { colour(it,it) }
            val ref=DoubleArray(3){c->samples.map { it[c] }.sorted()[samples.size/2]}
            if(samples.any { dist(it,ref)>40 }) return@associateWith null
            val outer=colour(0,0)
            if(dist(outer,ref)<65) return@associateWith null
            (2 until span step 3).map { y ->
                (0 until span).firstOrNull { x -> (x until minOf(x+3,span)).all { dist(colour(it,y),ref)<45 } } ?: span
            }
        }
        return profiles.mapValues { (name,profile) ->
            if(profile==null) Evidence.INCONCLUSIVE else {
                val others=profiles.filterKeys { it!=name }.values.filterNotNull()
                if(others.size<2) Evidence.INCONCLUSIVE else {
                    val deviations=profile.indices.map { i ->
                        val reference=others.map { it[i] }.sorted()[others.size/2]
                        abs(profile[i]-reference).toDouble()
                    }
                    if(deviations.count { it>card.width*.008 }>=3) Evidence.ASYMMETRIC else Evidence.COMPARABLE
                }
            }
        }
    }
}
