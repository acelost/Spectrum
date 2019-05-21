package com.acelost.demospectrum.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.acelost.demospectrum.R

class OrangeFragment : Fragment() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_orange, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (savedInstanceState == null) {
            childFragmentManager.beginTransaction()
                .add(R.id.child_fragment_container, RedFragment())
                .commit()
            OrangeDialogFragment().show(childFragmentManager, "orange dialog")
        }
    }

}