package com.acelost.demospectrum

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.acelost.demospectrum.fragments.*

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .add(GreenFragment(), "fragment without container")
                .addToBackStack(null)
                .commit()
            supportFragmentManager.beginTransaction()
                .replace(R.id.page_container, OrangeFragment())
                .addToBackStack(null)
                .commit()
            supportFragmentManager.beginTransaction()
                .add(R.id.page_container, BlueFragment())
                .commit()

            supportFragmentManager.beginTransaction()
                .add(R.id.footer_container, PurpleFragment())
                .commit()

            GreenDialogFragment().show(supportFragmentManager, "green dialog")

            startActivity(Intent(this, SecondaryActivity::class.java))
        }
    }
}
