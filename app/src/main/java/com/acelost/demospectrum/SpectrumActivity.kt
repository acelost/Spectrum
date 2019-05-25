package com.acelost.demospectrum

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.acelost.spectrum.Spectrum

abstract class SpectrumActivity : AppCompatActivity() {

    companion object {
        init {
            Spectrum.configure()
                .appendViewLocations(true)
                //.showViewHierarchy(false)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        //Spectrum.explore(this)
        super.onCreate(savedInstanceState)
    }

}