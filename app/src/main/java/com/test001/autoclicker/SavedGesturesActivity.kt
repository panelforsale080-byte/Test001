package com.test001.autoclicker

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Lists saved gesture JSON files with Play / Re-anchor & Play / Rename / Delete. */
class SavedGesturesActivity : AppCompatActivity() {

    private lateinit var adapter: Adapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_saved)

        val rv = findViewById<RecyclerView>(R.id.recycler)
        rv.layoutManager = LinearLayoutManager(this)
        adapter = Adapter(GestureStorage.list(this))
        rv.adapter = adapter

        findViewById<Button>(R.id.btn_refresh).setOnClickListener { reload() }
    }

    private fun reload() {
        adapter.items = GestureStorage.list(this)
        adapter.notifyDataSetChanged()
    }

    inner class Adapter(var items: List<File>) : RecyclerView.Adapter<Adapter.VH>() {

        inner class VH(v: View) : RecyclerView.ViewHolder(v) {
            val name: TextView = v.findViewById(R.id.item_name)
            val meta: TextView = v.findViewById(R.id.item_meta)
            val play: Button = v.findViewById(R.id.item_play)
            val anchorPlay: Button = v.findViewById(R.id.item_anchor_play)
            val rename: Button = v.findViewById(R.id.item_rename)
            val del: Button = v.findViewById(R.id.item_delete)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
            VH(LayoutInflater.from(parent.context).inflate(R.layout.item_gesture, parent, false))

        override fun getItemCount() = items.size

        override fun onBindViewHolder(h: VH, pos: Int) {
            val f = items[pos]
            val g = GestureStorage.load(f)
            val fmt = SimpleDateFormat("MMM d, HH:mm", Locale.getDefault())
            h.name.text = f.nameWithoutExtension
            h.meta.text = if (g != null)
                "${g.points.size} pts · ${g.duration}ms · ${fmt.format(Date(f.lastModified()))}"
            else "unreadable file"

            h.play.setOnClickListener {
                if (g == null) return@setOnClickListener
                GesturePlayer.play(g, AnchorStore.anchor, 1, 1f) { s ->
                    toast("play: $s")
                }
            }
            h.anchorPlay.setOnClickListener {
                if (g == null) return@setOnClickListener
                AnchorPicker.pick(this@SavedGesturesActivity) { p ->
                    GesturePlayer.play(g, p, 1, 1f) { s -> toast("anchor play: $s") }
                }
            }
            h.rename.setOnClickListener {
                val et = EditText(this@SavedGesturesActivity).apply { setText(f.nameWithoutExtension) }
                AlertDialog.Builder(this@SavedGesturesActivity)
                    .setTitle("Rename gesture").setView(et)
                    .setPositiveButton("OK") { _, _ ->
                        if (GestureStorage.rename(f, et.text.toString())) reload()
                        else toast("invalid name")
                    }
                    .setNegativeButton("Cancel", null).show()
            }
            h.del.setOnClickListener {
                AlertDialog.Builder(this@SavedGesturesActivity)
                    .setTitle("Delete ${f.nameWithoutExtension}?")
                    .setPositiveButton("Delete") { _, _ -> GestureStorage.delete(f); reload() }
                    .setNegativeButton("Cancel", null).show()
            }
        }
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()
}
