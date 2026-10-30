package com.israel.cowboyfriend.UI

import android.graphics.drawable.Animatable
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.text.BidiFormatter
import androidx.core.text.TextDirectionHeuristicsCompat
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.chip.ChipGroup
import com.israel.cowboyfriend.R
import com.israel.cowboyfriend.classes.TourHistory
import com.israel.cowboyfriend.classes.TourHistoryItem
import com.israel.cowboyfriend.classes.TourSummary
import com.israel.cowboyfriend.global.DialogSizing
import com.israel.cowboyfriend.global.getStringFromCalendar
import com.israel.cowboyfriend.viewmodel.MyViewModelSupbase
import java.util.Calendar

/**
 * The tour history screen: the tours of the ranch, newest first (a coordinator sees the tours of all the cowboys). a tap on
 * a tour opens its summary. a tour that is still open has an "end tour" button for the cowboy who started it and for a
 * coordinator / owner. it has no constructor arguments, so it survives a rotation.
 */
class TourHistoryDialogFragment : DialogFragment() {

    private var viewModel: MyViewModelSupbase? = null
    private var rcTours: RecyclerView? = null
    private var ivLoading: ImageView? = null
    private var tvEmpty: TextView? = null
    private var history: TourHistory? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_tour_history, container, false)
        viewModel = ViewModelProvider(requireActivity())[MyViewModelSupbase::class.java]
        rcTours = view.findViewById(R.id.rcTours)
        ivLoading = view.findViewById(R.id.ivHistoryLoading)
        tvEmpty = view.findViewById(R.id.tvHistoryEmpty)
        //one column in portrait, two in landscape (the same setting as the cow cards)
        rcTours?.layoutManager = GridLayoutManager(requireContext(), resources.getInteger(R.integer.cow_list_columns))
        view.findViewById<View>(R.id.ibHistoryClose).setOnClickListener { dismiss() }
        loadHistory()
        return view
    }

    //loads the tours: the horseshoe spins meanwhile, then the list (or a message)
    private fun loadHistory() {
        setLoading(true)
        tvEmpty?.visibility = View.GONE
        viewModel?.dbLoadTourHistory { loaded ->
            if (!isAdded) return@dbLoadTourHistory
            setLoading(false)
            history = loaded
            if (loaded == null) {
                showMessage(R.string.history_load_failed)
                rcTours?.adapter = null
            } else if (loaded.items.isEmpty()) {
                showMessage(R.string.history_empty)
                rcTours?.adapter = null
            } else {
                tvEmpty?.visibility = View.GONE
                rcTours?.adapter = TourAdapter(loaded)
            }
        }
    }

    private fun showMessage(textRes: Int) {
        tvEmpty?.setText(textRes)
        tvEmpty?.visibility = View.VISIBLE
    }

    private fun setLoading(loading: Boolean) {
        val spinner = ivLoading ?: return
        spinner.visibility = if (loading) View.VISIBLE else View.GONE
        val animation = spinner.drawable as? Animatable
        if (loading) animation?.start() else animation?.stop()
    }

    //the summary of a tour: the saved one for a closed tour, the live one ("so far") for a tour that is still open
    private fun openSummary(item: TourHistoryItem) {
        val saved = item.summaryJson?.let { TourSummary.fromJson(it) }
        if (saved != null) {
            showSummary(saved)
            return
        }
        viewModel?.dbTourSummary(item.id) { summary ->
            if (!isAdded) return@dbTourSummary
            if (summary != null) showSummary(summary) else showMessage(R.string.history_open_failed)
        }
    }

    private fun showSummary(summary: TourSummary) {
        TourSummaryDialogFragment.newInstance(summary).show(parentFragmentManager, "TourSummaryDialogFragment")
    }

    //asks before closing a tour, then ends it: the list is loaded again and the summary of the tour is shown
    private fun confirmEnd(item: TourHistoryItem) {
        val ctx = context ?: return
        val content = LayoutInflater.from(ctx).inflate(R.layout.dialog_confirm, null, false)
        content.findViewById<TextView>(R.id.tvConfirmTitle).setText(R.string.tour_end_title)
        content.findViewById<TextView>(R.id.tvConfirmMessage).text =
            getString(R.string.history_end_message, item.startedBy.substringBefore('@'))
        content.findViewById<TextView>(R.id.btnConfirmYes).setText(R.string.tour_end)
        content.findViewById<TextView>(R.id.btnConfirmNo).setText(R.string.tour_continue)
        val dialog = android.app.Dialog(ctx).apply {
            requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
            setContentView(content)
            window?.setBackgroundDrawable(ColorDrawable(android.graphics.Color.TRANSPARENT))
            DialogSizing.apply(this, 0.9f)
            window?.setDimAmount(0.55f)
        }
        content.findViewById<View>(R.id.btnConfirmNo).setOnClickListener { dialog.dismiss() }
        content.findViewById<View>(R.id.btnConfirmYes).setOnClickListener {
            dialog.dismiss()
            setLoading(true)
            viewModel?.dbEndTour(item.id) { summary ->
                if (!isAdded) return@dbEndTour
                setLoading(false)
                loadHistory()
                if (summary != null) showSummary(summary) else showMessage(R.string.tour_end_failed)
            }
        }
        dialog.show()
    }

    override fun onStart() {
        super.onStart()
        //the dialog covers the whole screen; the window is brown, so the status bar blends with the header
        dialog?.window?.apply {
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            setBackgroundDrawable(ColorDrawable(ContextCompat.getColor(requireContext(), R.color.ww_saddle_dark)))
        }
    }

    //the list of the tours: one card for each tour
    private inner class TourAdapter(private val data: TourHistory) : RecyclerView.Adapter<TourAdapter.Holder>() {

        inner class Holder(view: View) : RecyclerView.ViewHolder(view) {
            val strip: View = view.findViewById(R.id.vTourStrip)
            val date: TextView = view.findViewById(R.id.tvTourDate)
            val statusChip: TextView = view.findViewById(R.id.tvTourStatusChip)
            val who: TextView = view.findViewById(R.id.tvTourWho)
            val numbers: ChipGroup = view.findViewById(R.id.cgTourNumbers)
            val endButton: TextView = view.findViewById(R.id.btnTourEnd)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            Holder(LayoutInflater.from(parent.context).inflate(R.layout.item_tour, parent, false))

        override fun getItemCount() = data.items.size

        override fun onBindViewHolder(holder: Holder, position: Int) {
            val item = data.items[position]
            val context = holder.itemView.context
            val summary = item.summaryJson?.let { TourSummary.fromJson(it) }

            //the date and time of the start (numbers: kept left to right), who did the tour and how long it took
            val dateText = item.startedAtMillis?.let {
                getStringFromCalendar(Calendar.getInstance().apply { timeInMillis = it }, "dd/MM/yy HH:mm", context)
            }.orEmpty()
            holder.date.text = BidiFormatter.getInstance().unicodeWrap(dateText, TextDirectionHeuristicsCompat.LTR)
            val who = ArrayList<String>()
            who.add(item.startedBy.substringBefore('@'))
            summary?.let { who.add(it.durationText(context)) }
            holder.who.text = who.joinToString(" · ")

            //the band and the status chip: a tour in progress is brass with a chip, a cancelled one is grey brown
            holder.strip.setBackgroundColor(ContextCompat.getColor(context, when {
                item.isOpen -> R.color.ww_brass
                item.status == "cancelled" -> R.color.ww_saddle
                else -> R.color.ww_turquoise
            }))
            when {
                item.isOpen -> showStatus(holder.statusChip, R.string.history_status_open, R.drawable.shape_ww_chip_warn_bg, R.color.ww_saddle_dark)
                item.status == "cancelled" -> showStatus(holder.statusChip, R.string.history_status_cancelled, R.drawable.shape_ww_chip_info_bg, R.color.ww_ink)
                else -> holder.statusChip.visibility = View.GONE
            }

            //the main numbers of the saved summary
            holder.numbers.removeAllViews()
            if (summary != null) {
                holder.numbers.addView(numberChip(context, context.getString(R.string.history_chip_seen, summary.seen, summary.alive), R.drawable.shape_ww_chip_ok_bg, R.color.ww_turquoise_dark))
                if (summary.newCalves > 0) holder.numbers.addView(numberChip(context, context.getString(R.string.history_chip_calves, summary.newCalves), R.drawable.shape_ww_chip_info_bg, R.color.ww_ink))
                if (summary.newCorpses > 0) holder.numbers.addView(numberChip(context, context.getString(R.string.history_chip_corpses, summary.newCorpses), R.drawable.shape_ww_chip_dead_bg, R.color.ww_brass_light))
                if (summary.changed > 0) holder.numbers.addView(numberChip(context, context.getString(R.string.history_chip_changed, summary.changed), R.drawable.shape_ww_chip_info_bg, R.color.ww_ink))
            }
            holder.numbers.visibility = if (holder.numbers.childCount > 0) View.VISIBLE else View.GONE

            //a tap on the card opens the summary; an open tour that the user may end has the end button
            holder.itemView.setOnClickListener { openSummary(item) }
            holder.endButton.visibility = if (data.canEnd(item)) View.VISIBLE else View.GONE
            holder.endButton.setOnClickListener { confirmEnd(item) }
        }

        //the status chip of a tour (open / cancelled)
        private fun showStatus(chip: TextView, textRes: Int, backgroundRes: Int, colorRes: Int) {
            chip.setText(textRes)
            chip.setBackgroundResource(backgroundRes)
            chip.setTextColor(ContextCompat.getColor(chip.context, colorRes))
            chip.visibility = View.VISIBLE
        }

        //a small number chip of the card (the same chips as the status chips of the cow card)
        private fun numberChip(context: android.content.Context, text: String, backgroundRes: Int, colorRes: Int): TextView {
            return TextView(context, null, 0, R.style.Ww_StatusChip).apply {
                this.text = text
                setBackgroundResource(backgroundRes)
                setTextColor(ContextCompat.getColor(context, colorRes))
            }
        }
    }
}
