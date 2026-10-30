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
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.chip.ChipGroup
import com.israel.cowboyfriend.R
import com.israel.cowboyfriend.UI.widget.TrendBarsView
import com.israel.cowboyfriend.classes.CowDetails
import com.israel.cowboyfriend.classes.HealthIssues
import com.israel.cowboyfriend.classes.RanchStats
import com.israel.cowboyfriend.classes.TourSummary
import com.israel.cowboyfriend.global.PdfExporter
import com.israel.cowboyfriend.global.getStringFromCalendar
import com.israel.cowboyfriend.viewmodel.MyViewModelSupbase
import java.util.Calendar

/**
 * The ranch dashboard: the whole herd on one screen - the herd by type, what needs attention, the health problems, births
 * and deaths by year of birth, the trend of the tour coverage and the heat map. the numbers are calculated from the list
 * of the cows ([RanchStats]) and from the saved summaries of the tours. it can be shared as a text or as a PDF.
 * it has no constructor arguments, so it survives a rotation.
 */
class RanchDashboardDialogFragment : DialogFragment() {

    private var viewModel: MyViewModelSupbase? = null
    private var rootView: View? = null
    private var stats: RanchStats? = null
    //the trend of the tours as text lines for the shared text: the coverage in percent of the last tours, oldest first
    private var trendPercents: List<Int> = emptyList()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_ranch_dashboard, container, false)
        rootView = view
        viewModel = ViewModelProvider(requireActivity())[MyViewModelSupbase::class.java]

        view.findViewById<View>(R.id.ibDashClose).setOnClickListener { dismiss() }
        view.findViewById<View>(R.id.btnDashShowAttention).setOnClickListener {
            //the cattle tour screen shows the cows that need attention (the same result as the tour summary)
            parentFragmentManager.setFragmentResult(
                TourSummaryDialogFragment.RESULT_KEY,
                Bundle().apply { putString(TourSummaryDialogFragment.RESULT_ACTION, TourSummaryDialogFragment.ACTION_SHOW_ATTENTION) }
            )
            dismiss()
        }
        view.findViewById<View>(R.id.btnDashHeatAll).setOnClickListener { showHeatMap(MyViewModelSupbase.HEAT_ALL) }
        view.findViewById<View>(R.id.btnDashHeatAttention).setOnClickListener { showHeatMap(MyViewModelSupbase.HEAT_ATTENTION) }
        view.findViewById<View>(R.id.btnDashShareText).setOnClickListener { shareText() }
        view.findViewById<View>(R.id.btnDashSharePdf).setOnClickListener { sharePdf() }

        //the cows: the list of the cattle tour screen, loaded again when it is not there yet; the numbers follow it
        viewModel?._cowsDetails?.observe(viewLifecycleOwner) { cows -> if (cows != null) showStats(cows) }
        if (viewModel?._cowsDetails?.value.isNullOrEmpty()) viewModel?.dbGetCowsDetails()
        loadTrend()
        return view
    }

    //calculates the numbers of the cows and shows them
    private fun showStats(cows: List<CowDetails>) {
        val view = rootView ?: return
        val stats = RanchStats(cows)
        this.stats = stats
        showHerd(view, stats)
        showAttention(view, stats)
        showHealth(view, stats)
        showYears(view, stats)
    }

    //the herd: the living cows, by type, and the dead ones
    private fun showHerd(view: View, stats: RanchStats) {
        view.findViewById<TextView>(R.id.tvDashAlive).text = stats.alive.toString()
        val chips = view.findViewById<ChipGroup>(R.id.cgDashKinds)
        chips.removeAllViews()
        val names = resources.getStringArray(R.array.cow_type)
        for (kind in names.indices) {
            val count = stats.aliveByKind[kind] ?: 0
            if (count > 0) chips.addView(statusChip("${names[kind]} $count", R.drawable.shape_ww_chip_info_bg, R.color.ww_ink))
        }
        if (stats.dead > 0) {
            chips.addView(statusChip(getString(R.string.dash_dead, stats.dead), R.drawable.shape_ww_chip_dead_bg, R.color.ww_brass_light))
        }
    }

    //what needs attention: a row for every problem that exists, or "nothing needs attention"
    private fun showAttention(view: View, stats: RanchStats) {
        val rows = view.findViewById<LinearLayout>(R.id.llDashAttention)
        rows.removeAllViews()
        for (key in TourSummary.ATTENTION_KEYS) {
            val count = stats.attentionByKey[key] ?: 0
            if (count == 0) continue
            val row = layoutInflater.inflate(R.layout.item_summary_issue, rows, false)
            row.findViewById<ImageView>(R.id.ivIssueIcon).setImageResource(attentionIcon(key))
            row.findViewById<TextView>(R.id.tvIssueLabel).setText(TourSummary.attentionLabelRes(key))
            row.findViewById<TextView>(R.id.tvIssueCount).text = count.toString()
            rows.addView(row)
        }
        val any = stats.attentionCows > 0
        view.findViewById<View>(R.id.tvDashAllGood).visibility = if (any) View.GONE else View.VISIBLE
        view.findViewById<View>(R.id.btnDashShowAttention).visibility = if (any) View.VISIBLE else View.GONE
    }

    //the health problems, the most common first; the bar is the share of the most common one
    private fun showHealth(view: View, stats: RanchStats) {
        val list = view.findViewById<LinearLayout>(R.id.llDashHealth)
        list.removeAllViews()
        val title = view.findViewById<TextView>(R.id.tvDashHealthTitle)
        title.text = getString(R.string.dash_health) +
                if (stats.cowsWithHealthProblem > 0) " · " + getString(R.string.dash_health_count, stats.cowsWithHealthProblem) else ""
        view.findViewById<View>(R.id.tvDashHealthEmpty).visibility = if (stats.healthByCode.isEmpty()) View.VISIBLE else View.GONE
        val max = stats.healthByCode.maxOfOrNull { it.second } ?: 1
        for ((code, count) in stats.healthByCode.take(MAX_ROWS)) {
            addBarRow(list, HealthIssues.labels(requireContext(), listOf(code)).first(), count * 100f / max, count.toString())
        }
    }

    //births and deaths: for every year of birth the cows registered (in the label) and the share of the dead ones (the bar)
    private fun showYears(view: View, stats: RanchStats) {
        val list = view.findViewById<LinearLayout>(R.id.llDashYears)
        list.removeAllViews()
        for (year in stats.years.take(MAX_ROWS)) {
            //the year and the number of the cows are numbers: kept left to right, so "2026 (19)" reads the same in Hebrew
            val label = androidx.core.text.BidiFormatter.getInstance()
                .unicodeWrap("${year.year} (${year.registered})", androidx.core.text.TextDirectionHeuristicsCompat.LTR)
            addBarRow(list, label, year.mortalityPercent.toFloat(), "${year.mortalityPercent}%")
        }
    }

    //the trend of the tour coverage: the saved summaries of the closed tours (the last ones), oldest first
    private fun loadTrend() {
        viewModel?.dbLoadTourHistory { history ->
            if (!isAdded) return@dbLoadTourHistory
            val view = rootView ?: return@dbLoadTourHistory
            val closed = history?.items.orEmpty()
                .filter { it.status == "closed" && it.summaryJson != null }
                .mapNotNull { item -> TourSummary.fromJson(item.summaryJson!!)?.let { item to it } }
                .filter { it.second.alive > 0 }
                .take(MAX_TOURS)
                .reversed()
            val bars = view.findViewById<TrendBarsView>(R.id.trendBars)
            val text = view.findViewById<TextView>(R.id.tvDashTrendText)
            val empty = view.findViewById<View>(R.id.tvDashTrendEmpty)
            if (closed.isEmpty()) {
                bars.visibility = View.GONE
                text.visibility = View.GONE
                empty.visibility = View.VISIBLE
                return@dbLoadTourHistory
            }
            bars.visibility = View.VISIBLE
            text.visibility = View.VISIBLE
            empty.visibility = View.GONE
            val percents = closed.map { (_, s) -> Math.round(s.seen * 100f / s.alive) }
            trendPercents = percents
            val labels = closed.map { (item, _) ->
                getStringFromCalendar(Calendar.getInstance().apply { timeInMillis = item.startedAtMillis ?: 0L }, "dd/MM", requireContext())
            }
            bars.setData(percents, labels)
            val last = percents.last()
            val delta = if (percents.size > 1) last - percents[percents.size - 2] else null
            text.text = getString(R.string.dash_trend_last, last) + when {
                delta == null -> ""
                delta > 0 -> " · " + getString(R.string.dash_trend_up, delta)
                delta < 0 -> " · " + getString(R.string.dash_trend_down, -delta)
                else -> " · " + getString(R.string.dash_trend_same)
            }
        }
    }

    //one row with a bar: [percent] (0-100) is how much of the bar is filled
    private fun addBarRow(parent: LinearLayout, label: String, percent: Float, value: String) {
        val row = layoutInflater.inflate(R.layout.item_dash_bar, parent, false)
        row.findViewById<TextView>(R.id.tvBarLabel).text = label
        row.findViewById<TextView>(R.id.tvBarValue).text = value
        row.findViewById<View>(R.id.vBarFill).let {
            (it.layoutParams as LinearLayout.LayoutParams).weight = percent.coerceIn(0f, 100f)
        }
        parent.addView(row)
    }

    //a status chip (the same chips as the cow card)
    private fun statusChip(text: String, backgroundRes: Int, colorRes: Int): TextView {
        return TextView(requireContext(), null, 0, R.style.Ww_StatusChip).apply {
            this.text = text
            setBackgroundResource(backgroundRes)
            setTextColor(ContextCompat.getColor(context, colorRes))
        }
    }

    private fun attentionIcon(key: String): Int = when (key) {
        "calf_without_mom" -> R.drawable.ic_chip_cow
        "calf_without_tag" -> R.drawable.ic_chip_tag
        "health_issue" -> R.drawable.ic_chip_health
        "not_seen_14d", "never_seen" -> R.drawable.ic_chip_eye
        else -> R.drawable.ic_chip_pin
    }

    //opens the map screen with the heat layer: the mode waits in the view model, the main screen switches to the map tab
    private fun showHeatMap(mode: Int) {
        viewModel?.pendingHeatMode = mode
        parentFragmentManager.setFragmentResult(RESULT_DASHBOARD_ACTION, Bundle().apply { putString(RESULT_ACTION_KEY, ACTION_SHOW_MAP) })
        dismiss()
    }

    //the dashboard as a text: the numbers, one line each
    private fun buildText(stats: RanchStats): String {
        val lines = ArrayList<String>()
        lines.add(getString(R.string.dash_title))
        lines.add("${getString(R.string.dash_herd)}: ${stats.alive} ${getString(R.string.dash_alive)}" +
                if (stats.dead > 0) " · " + getString(R.string.dash_dead, stats.dead) else "")
        val names = resources.getStringArray(R.array.cow_type)
        lines.add(names.indices.filter { (stats.aliveByKind[it] ?: 0) > 0 }.joinToString(" · ") { "${names[it]} ${stats.aliveByKind[it]}" })
        if (stats.attentionCows > 0) {
            lines.add(getString(R.string.summary_attention) + " (" + stats.attentionCows + "):")
            for (key in TourSummary.ATTENTION_KEYS) {
                val count = stats.attentionByKey[key] ?: 0
                if (count > 0) lines.add("- " + getString(TourSummary.attentionLabelRes(key)) + ": " + count)
            }
        }
        if (stats.healthByCode.isNotEmpty()) {
            lines.add(getString(R.string.dash_health) + ":")
            for ((code, count) in stats.healthByCode.take(MAX_ROWS)) {
                lines.add("- " + HealthIssues.labels(requireContext(), listOf(code)).first() + ": " + count)
            }
        }
        if (stats.years.isNotEmpty()) {
            lines.add(getString(R.string.dash_years) + ":")
            for (year in stats.years.take(MAX_ROWS)) lines.add("- ${year.year}: ${year.registered} · ${year.mortalityPercent}%")
        }
        if (trendPercents.isNotEmpty()) lines.add(getString(R.string.dash_trend_last, trendPercents.last()))
        return lines.joinToString("\n")
    }

    private fun shareText() {
        val stats = stats ?: return
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, buildText(stats))
        }
        startActivity(Intent.createChooser(intent, getString(R.string.dash_share_text)))
    }

    private fun sharePdf() {
        val content = rootView?.findViewById<View>(R.id.llDashContent) ?: return
        val file = PdfExporter.createPdf(requireActivity(), content, "ranch_dashboard")
        if (file == null) {
            Toast.makeText(requireContext(), R.string.dash_pdf_failed, Toast.LENGTH_SHORT).show()
            return
        }
        PdfExporter.sharePdf(requireActivity(), file, getString(R.string.dash_share_pdf))
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
        //fragment result: the user wants to see the map (the heat mode waits in the view model)
        const val RESULT_DASHBOARD_ACTION = "dashboard_action"
        const val RESULT_ACTION_KEY = "action"
        const val ACTION_SHOW_MAP = "show_map"
        //how many rows a list of the dashboard shows, and how many tours the trend shows
        private const val MAX_ROWS = 8
        private const val MAX_TOURS = 8
    }
}
