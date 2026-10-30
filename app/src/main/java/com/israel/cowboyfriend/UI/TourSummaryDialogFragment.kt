package com.israel.cowboyfriend.UI

import android.content.Intent
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.DialogFragment
import com.google.android.material.chip.ChipGroup
import com.israel.cowboyfriend.R
import com.israel.cowboyfriend.classes.TourSummary
import com.israel.cowboyfriend.global.getStringFromCalendar
import java.util.Calendar
import java.util.Locale

/**
 * The summary screen of a tour (shown when a tour ends): the coverage (how many cows were seen and which were not), the
 * number tiles, what needs attention, and the comparison with the previous tour. it can be shared as a text.
 * it has no constructor arguments (the summary is kept as json in the arguments), so it survives a rotation.
 * the button "show the cows that need attention" reports to the opener with a fragment result ([RESULT_KEY])
 */
class TourSummaryDialogFragment : DialogFragment() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_tour_summary, container, false)
        val summary = arguments?.getString(ARG_SUMMARY)?.let { TourSummary.fromJson(it) }
        if (summary == null) {
            dismissAllowingStateLoss()
            return view
        }
        showSummary(view, summary)
        return view
    }

    //fills all the numbers, the chips and the rows of the screen
    private fun showSummary(view: View, summary: TourSummary) {
        val context = requireContext()

        //header: the date and the time of the start, how long it took and who did the tour
        val subtitle = ArrayList<String>()
        summary.startedAtMillis?.let {
            //the date and time are numbers: they are kept left to right, so a right to left line does not turn them around
            val dateText = getStringFromCalendar(Calendar.getInstance().apply { timeInMillis = it }, "dd/MM/yy HH:mm", context)
            subtitle.add(androidx.core.text.BidiFormatter.getInstance().unicodeWrap(dateText, androidx.core.text.TextDirectionHeuristicsCompat.LTR))
        }
        subtitle.add(summary.durationText(context))
        if (summary.startedBy.isNotBlank()) subtitle.add(getString(R.string.summary_by, summary.startedBy.substringBefore('@')))
        view.findViewById<TextView>(R.id.tvSummarySubtitle).text = subtitle.joinToString(" · ")

        //coverage: N of M seen, the bar, and the numbers of the cows that were not seen
        view.findViewById<TextView>(R.id.tvSummarySeen).text = getString(R.string.summary_seen_of, summary.seen, summary.alive)
        val percent = if (summary.alive > 0) (summary.seen * 100f / summary.alive).coerceIn(0f, 100f) else 0f
        view.findViewById<View>(R.id.vCoverageFill).let {
            (it.layoutParams as LinearLayout.LayoutParams).weight = percent
            it.requestLayout()
        }
        //the cows that were seen: turquoise circles with their numbers (hidden when the summary has no numbers)
        if (summary.seenNumbers.isNotEmpty()) {
            val seenTitle = view.findViewById<TextView>(R.id.tvSummarySeenTitle)
            val seenChips = view.findViewById<ChipGroup>(R.id.cgSummarySeen)
            seenTitle.text = getString(R.string.summary_seen_title, summary.seen)
            seenTitle.visibility = View.VISIBLE
            seenChips.visibility = View.VISIBLE
            for (number in summary.seenNumbers.take(MAX_NUMBER_CHIPS)) seenChips.addView(numberChip(number.toString(), true))
            val moreSeen = summary.seen - minOf(summary.seenNumbers.size, MAX_NUMBER_CHIPS)
            if (moreSeen > 0) view.findViewById<TextView>(R.id.tvSummarySeenMore).apply {
                text = getString(R.string.summary_and_more, moreSeen)
                visibility = View.VISIBLE
            }
        }
        val notSeenTitle = view.findViewById<TextView>(R.id.tvSummaryNotSeenTitle)
        val notSeenChips = view.findViewById<ChipGroup>(R.id.cgSummaryNotSeen)
        if (summary.notSeenCount > 0) {
            notSeenTitle.text = getString(R.string.summary_not_seen, summary.notSeenCount)
            for (number in summary.notSeenNumbers.take(MAX_NUMBER_CHIPS)) notSeenChips.addView(numberChip(number.toString()))
            val more = summary.notSeenCount - minOf(summary.notSeenNumbers.size, MAX_NUMBER_CHIPS)
            if (more > 0) view.findViewById<TextView>(R.id.tvSummaryNotSeenMore).apply {
                text = getString(R.string.summary_and_more, more)
                visibility = View.VISIBLE
            }
        } else {
            notSeenTitle.visibility = View.GONE
            notSeenChips.visibility = View.GONE
        }

        //the four tiles
        view.findViewById<TextView>(R.id.tvTileCalves).text = summary.newCalves.toString()
        view.findViewById<TextView>(R.id.tvTileCorpses).text = summary.newCorpses.toString()
        view.findViewById<TextView>(R.id.tvTileChanged).text = summary.changed.toString()
        view.findViewById<TextView>(R.id.tvTileComments).text = summary.commentsAdded.toString()

        //needs attention: a row for every problem that exists, or "nothing needs attention"
        val rows = summary.attentionRows()
        val llRows = view.findViewById<LinearLayout>(R.id.llSummaryAttention)
        for ((key, count) in rows) {
            val row = layoutInflater.inflate(R.layout.item_summary_issue, llRows, false)
            row.findViewById<ImageView>(R.id.ivIssueIcon).setImageResource(attentionIcon(key))
            row.findViewById<TextView>(R.id.tvIssueLabel).setText(TourSummary.attentionLabelRes(key))
            row.findViewById<TextView>(R.id.tvIssueCount).text = count.toString()
            llRows.addView(row)
        }
        view.findViewById<View>(R.id.tvSummaryAllGood).visibility = if (rows.isEmpty()) View.VISIBLE else View.GONE
        view.findViewById<View>(R.id.btnSummaryShowAttention).visibility = if (rows.isEmpty()) View.GONE else View.VISIBLE

        //the comparison with the previous tour: seen, new calves and calves without a mother, as signed differences
        if (summary.hasPrevious) {
            val parts = ArrayList<String>()
            summary.vsPrevious("seen")?.let { parts.add(getString(R.string.summary_vs_seen) + ": " + signed(it)) }
            summary.vsPrevious("new_calves")?.let { parts.add(getString(R.string.summary_vs_calves) + ": " + signed(it)) }
            summary.vsPrevious("calf_without_mom")?.let { parts.add(getString(R.string.summary_vs_no_mom) + ": " + signed(it)) }
            if (parts.isNotEmpty()) {
                view.findViewById<TextView>(R.id.tvSummaryVs).text = parts.joinToString(" · ")
                view.findViewById<View>(R.id.llSummaryVs).visibility = View.VISIBLE
            }
        }

        //buttons
        view.findViewById<View>(R.id.ibSummaryClose).setOnClickListener { dismiss() }
        view.findViewById<View>(R.id.btnSummaryClose).setOnClickListener { dismiss() }
        view.findViewById<View>(R.id.btnSummaryShare).setOnClickListener { shareSummary(summary) }
        view.findViewById<View>(R.id.btnSummaryShowAttention).setOnClickListener {
            parentFragmentManager.setFragmentResult(RESULT_KEY, Bundle().apply { putString(RESULT_ACTION, ACTION_SHOW_ATTENTION) })
            dismiss()
        }
    }

    //a small chip with the number of a cow (sand, like the not chosen health chips)
    //(turquoise with light text when the cow was seen in the tour)
    private fun numberChip(text: String, seen: Boolean = false): TextView {
        return TextView(requireContext(), null, 0, R.style.Ww_HealthChip).apply {
            this.text = text
            isClickable = false
            if (seen) {
                setBackgroundResource(R.drawable.shape_ww_seen_chip)
                setTextColor(ContextCompat.getColor(context, R.color.ww_parchment))
            }
        }
    }

    //the sign of a difference: +3, -2, 0
    private fun signed(value: Int): String = String.format(Locale.US, "%+d", value).let { if (value == 0) "0" else it }

    private fun attentionIcon(key: String): Int = when (key) {
        "calf_without_mom" -> R.drawable.ic_chip_cow
        "calf_without_tag" -> R.drawable.ic_chip_tag
        "health_issue" -> R.drawable.ic_chip_health
        "not_seen_14d", "never_seen" -> R.drawable.ic_chip_eye
        else -> R.drawable.ic_chip_pin
    }

    //shares the summary as a text with the share sheet of the phone (WhatsApp is in it when it is installed)
    private fun shareSummary(summary: TourSummary) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, summary.toShareText(requireContext()))
        }
        startActivity(Intent.createChooser(intent, getString(R.string.summary_share)))
    }

    override fun onStart() {
        super.onStart()
        //the dialog covers the whole screen; the window is brown, so the status bar blends with the header
        dialog?.window?.apply {
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            setBackgroundDrawable(ColorDrawable(ContextCompat.getColor(requireContext(), R.color.ww_saddle_dark)))
        }
    }

    companion object {
        private const val ARG_SUMMARY = "summary"
        //fragment result: the user wants the list of the cows that need attention
        const val RESULT_KEY = "tour_summary_result"
        const val RESULT_ACTION = "action"
        const val ACTION_SHOW_ATTENTION = "show_attention"
        //how many numbers of the cows that were not seen are shown as chips
        private const val MAX_NUMBER_CHIPS = 14

        fun newInstance(summary: TourSummary) = TourSummaryDialogFragment().apply {
            arguments = Bundle().apply { putString(ARG_SUMMARY, summary.toString()) }
        }
    }
}
