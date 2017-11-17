package com.example.androidthings.peripherals.companion

import android.content.DialogInterface
import android.database.DataSetObserver
import android.os.Bundle
import android.support.design.widget.Snackbar
import android.support.v7.app.AlertDialog
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ListView
import android.widget.TextView
import com.firebase.ui.database.FirebaseListAdapter
import com.firebase.ui.database.FirebaseListOptions
import com.google.firebase.FirebaseApp
import com.google.firebase.database.FirebaseDatabase
import kotlinx.android.synthetic.main.activity_main.*


class MainActivity : AppCompatActivity() {

    var mAdapter: FirebaseListAdapter<String>? = null
    var mList: ListView? = null
    var mButton: Button? = null
    var mRoot: View? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        FirebaseApp.initializeApp(this)

        setContentView(R.layout.activity_main)
        setSupportActionBar(toolbar)

        fab.setOnClickListener(mWaterClickListener)

        mAdapter = setupFirebaseAdapter()
        mList = findViewById(R.id.gardening_list)

        mAdapter?.registerDataSetObserver(object : DataSetObserver() {
            override fun onChanged() {
                super.onChanged()
                val count = mAdapter?.count ?: 0
                mList?.smoothScrollToPosition(count.minus(1))
            }
        })

        mList?.adapter = mAdapter

        mButton = findViewById(R.id.button_give_water)
        mButton?.setOnClickListener(mWaterClickListener)
    }

    override fun onStart() {
        super.onStart()
        mAdapter?.startListening()
    }

    override fun onStop() {
        super.onStop()
        mAdapter?.stopListening()
    }

    private fun setupFirebaseAdapter(): FirebaseListAdapter<String>? {
        val query = FirebaseDatabase.getInstance().reference.child("gardening")
        val options = FirebaseListOptions.Builder<String>()
                .setQuery(query, String::class.java)
                .setLayout(android.R.layout.simple_list_item_1)
                .build()

        return object : FirebaseListAdapter<String>(options) {
            override fun populateView(v: View?, model: String?, position: Int) {
                Log.d("TAG", "model: " + model)
                (v as TextView).text = model
            }
        }
    }

    private var mWaterClickListener = View.OnClickListener {
        val builder = AlertDialog.Builder(this@MainActivity)
        builder.setMessage("Are you sure to water?")
                .setIcon(R.drawable.water)
                .setPositiveButton("Yes", dialogClickListener)
                .setNegativeButton("No") { d, _ -> d.dismiss() }.show()
    }

    private var dialogClickListener = DialogInterface.OnClickListener { _, _ ->
        mRoot = findViewById(R.id.root)
        Snackbar.make(mRoot!!, "Succesfully sent water command", Snackbar.LENGTH_LONG).show()
        FirebaseDatabase.getInstance().reference.child("command").setValue(true)
    }
}
