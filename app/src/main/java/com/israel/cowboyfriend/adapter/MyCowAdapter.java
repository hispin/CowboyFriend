package com.israel.cowboyfriend.adapter;

import static com.israel.cowboyfriend.global.ConstsKt.ACTION_EDIT_COW_TYPE;
import static com.israel.cowboyfriend.global.ConstsKt.ACTION_SHOW_COMMENT_TYPE;
import static com.israel.cowboyfriend.global.ConstsKt.ACTION_SHOW_HEALTH_TYPE;
import static com.israel.cowboyfriend.global.ConstsKt.ACTION_SHOW_LARGE_TYPE;
import static com.israel.cowboyfriend.global.ConstsKt.NONE_TYPE;
import static com.israel.cowboyfriend.global.SysMethodDateKt.getStringFromCalendar;

import android.content.Context;
import android.content.res.ColorStateList;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.israel.cowboyfriend.R;
import com.israel.cowboyfriend.classes.CowDetails;
import com.israel.cowboyfriend.classes.HealthIssues;
import com.israel.cowboyfriend.global.CowLabels;
import com.israel.cowboyfriend.interfaces.InterOnItemClickListener;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Objects;


/**
 * The list of cow cards. A card only SHOWS the data (colored chips and icons); everything is edited in the edit
 * window that the fragment opens when the card, or its pencil badge, is tapped.
 */
public class MyCowAdapter extends RecyclerView.Adapter<MyCowAdapter.MyViewHolder> {


    private ArrayList<CowDetails> cows;
    private InterOnItemClickListener listener;


    /**
     * Initialize the dataset of the Adapter.
     *
     * @param cows String[] containing the data to populate views to be used
     * by RecyclerView.
     */

    public MyCowAdapter(ArrayList<CowDetails> cows, Context context, InterOnItemClickListener listener) {
        this.cows = cows;
        this.listener = listener;
    }


    public void setCows(ArrayList<CowDetails> cows) {
        this.cows = cows;
    }

    // Create new views (invoked by the layout manager)
    @NonNull
    @Override
    public MyViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_cow, parent, false);

        return new MyViewHolder(view,parent.getContext());
    }

    // Replace the contents of a view (invoked by the layout manager)
    @Override
    public void onBindViewHolder(@NonNull MyViewHolder holder, int position) {
        holder.bind(cows.get(position), listener,position);
    }

    // Return the size of your dataset (invoked by the layout manager)
    @Override
    public int getItemCount() {
        return cows.size();
    }

    /**
     * Provide a reference to the type of views that you are using
     * (custom ViewHolder).
     */
    public static class MyViewHolder extends RecyclerView.ViewHolder{

        Context context;
        ImageView ivCowImg;
        ImageView ivEditCow;
        TextView tvCowNum;
        //the last two digits of the birth year, next to the number
        TextView tvCowYear;
        TextView tvCowType;
        TextView tvGender;
        TextView tvMonNum;
        TextView tvLastSeen;
        TextView tvWithMomStatus;
        TextView tvEarTagStatus;
        TextView tvCorpseStamp;
        TextView tvLastDateLocation;
        TextView tvComment;
        //health problems of the cow (rust chip, hidden when there is none)
        TextView tvHealth;
        //colored top band of the card (turquoise = seen today, brass = not seen today, iron = corpse)
        View vSeenStrip;

        public MyViewHolder(@NonNull View itemView, Context context) {
            super(itemView);
            this.context = context;
            ivCowImg = itemView.findViewById(R.id.ivCowImg);
            ivEditCow = itemView.findViewById(R.id.ivEditCow);
            tvCowNum = itemView.findViewById(R.id.tvCowNum);
            tvCowYear = itemView.findViewById(R.id.tvCowYear);
            tvCowType = itemView.findViewById(R.id.tvCowType);
            tvGender = itemView.findViewById(R.id.tvGender);
            tvMonNum = itemView.findViewById(R.id.tvMonNum);
            tvLastSeen = itemView.findViewById(R.id.tvLastSeen);
            tvWithMomStatus = itemView.findViewById(R.id.tvWithMomStatus);
            tvEarTagStatus = itemView.findViewById(R.id.tvEarTagStatus);
            tvCorpseStamp = itemView.findViewById(R.id.tvCorpseStamp);
            tvLastDateLocation = itemView.findViewById(R.id.tvLastDateLocation);
            tvComment = itemView.findViewById(R.id.tvComment);
            tvHealth = itemView.findViewById(R.id.tvHealth);
            vSeenStrip = itemView.findViewById(R.id.vSeenStrip);
        }

        public void bind(final CowDetails item, final InterOnItemClickListener listener, int position) {
            //the card number: only the value, the "№" sign replaces the long label
            tvCowNum.setText(String.format(context.getString(R.string.card_cow_number),
                    item.getNumber() != null ? String.valueOf(item.getNumber()) : "-"));

            //the birth year (from created_at) as two digits next to the number: 2025 -> 25
            if(item.getCreated_at()!=null){
                Calendar birth = Calendar.getInstance();
                birth.setTimeInMillis(item.getCreated_at());
                int year = birth.get(Calendar.YEAR);
                tvCowYear.setText(String.format(java.util.Locale.US, "%02d", year % 100));
                tvCowYear.setContentDescription(String.format(context.getString(R.string.birth_year_description), year));
                tvCowYear.setVisibility(View.VISIBLE);
            }else{
                tvCowYear.setVisibility(View.GONE);
            }

            //the cow type and the gender are saved in Hebrew: they are shown in the language of the app
            String cowType = item.getCowType();
            if(cowType!=null && !cowType.trim().isEmpty()){
                tvCowType.setText(CowLabels.INSTANCE.typeLabel(context, cowType));
                tvCowType.setVisibility(View.VISIBLE);
            }else{
                tvCowType.setVisibility(View.GONE);
            }

            //the mom chip is shown only when the cow has a mom number
            if(item.getNumber_mom()!=null){
                tvMonNum.setText(String.format(context.getString(R.string.card_mom), String.valueOf(item.getNumber_mom())));
                tvMonNum.setVisibility(View.VISIBLE);
            }else{
                tvMonNum.setVisibility(View.GONE);
            }

            //the gender chip is shown only when the gender is known
            if(item.getGender()!=null && !item.getGender().trim().isEmpty()){
                tvGender.setText(CowLabels.INSTANCE.genderLabel(context, item.getGender()));
                tvGender.setVisibility(View.VISIBLE);
            }else{
                tvGender.setVisibility(View.GONE);
            }

            //the with mom and ear tag chips exist only for calves
            boolean isCalf = CowLabels.INSTANCE.typeIndex(cowType) == CowLabels.TYPE_CALF;
            boolean isCorpse = item.isCorpse();

            showSeenAndStrip(item, isCorpse);

            //corpse: a dark stamp with a skull; the seen / with mom / ear tag chips are not relevant for a dead cow
            tvCorpseStamp.setVisibility(isCorpse ? View.VISIBLE : View.GONE);

            if(isCalf && !isCorpse){
                //with mom: green when the cow was seen with its mother at the last visit, red when not
                boolean withMom = item.getWith_mom()!=null
                        && Objects.equals(item.getLast_seen_at(), item.getWith_mom());
                setChip(tvWithMomStatus,
                        withMom ? R.drawable.shape_ww_chip_ok_bg : R.drawable.shape_ww_chip_bad_bg,
                        R.drawable.ic_chip_cow,
                        withMom ? R.color.ww_turquoise_dark : R.color.ww_rust_dark,
                        withMom ? R.string.status_with_mom : R.string.status_no_mom);
                tvWithMomStatus.setVisibility(View.VISIBLE);

                //ear tag: green tag when the calf is marked, red when not
                boolean marked = Boolean.TRUE.equals(item.isMarkedTag());
                setChip(tvEarTagStatus,
                        marked ? R.drawable.shape_ww_chip_ok_bg : R.drawable.shape_ww_chip_bad_bg,
                        R.drawable.ic_chip_tag,
                        marked ? R.color.ww_turquoise_dark : R.color.ww_rust_dark,
                        marked ? R.string.status_tag_yes : R.string.status_tag_no);
                tvEarTagStatus.setVisibility(View.VISIBLE);
            }else{
                tvWithMomStatus.setVisibility(View.GONE);
                tvEarTagStatus.setVisibility(View.GONE);
            }

            //the location chip shows only the date (the pin icon explains it); hidden when there is no location yet
            if(item.getLocation_updated_at()!=null){
                Calendar date = Calendar.getInstance();
                date.setTimeInMillis(item.getLocation_updated_at());
                tvLastDateLocation.setText(getStringFromCalendar(date, "dd/MM/yy", context));
                tvLastDateLocation.setVisibility(View.VISIBLE);
            }else{
                tvLastDateLocation.setVisibility(View.GONE);
            }

            //comment: "has a comment" with a speaker (tap = the comment window, read aloud) or a faded "no comment"
            boolean hasComment = item.getComment()!=null && !item.getComment().trim().isEmpty();
            tvComment.setText(hasComment ? R.string.has_comment : R.string.status_no_comment);
            tvComment.setCompoundDrawablesRelativeWithIntrinsicBounds(
                    hasComment ? R.drawable.ic_chip_speaker : R.drawable.ic_chip_note, 0, 0, 0);
            tvComment.setAlpha(hasComment ? 1f : 0.55f);
            tvComment.setClickable(hasComment);
            tvComment.setOnClickListener(hasComment ? new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    listener.onItemClick(item,NONE_TYPE,position,ACTION_SHOW_COMMENT_TYPE);
                }
            } : null);

            //health problems: one rust chip - the name of the problem when there is one, or the number of problems; tap = the
            //list of them. a corpse shows no health problems
            final java.util.List<String> health = item.getHealthIssues();
            if(!isCorpse && health!=null && !health.isEmpty()){
                setChip(tvHealth, R.drawable.shape_ww_chip_bad_bg, R.drawable.ic_chip_health,
                        R.color.ww_rust_dark, 0);
                tvHealth.setText(health.size() == 1
                        ? HealthIssues.INSTANCE.labels(context, health).get(0)
                        : context.getString(R.string.health_chip_count, health.size()));
                tvHealth.setVisibility(View.VISIBLE);
                tvHealth.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        listener.onItemClick(item,NONE_TYPE,position,ACTION_SHOW_HEALTH_TYPE);
                    }
                });
            }else{
                tvHealth.setVisibility(View.GONE);
                tvHealth.setOnClickListener(null);
            }

            //the photo opens the large picture, the pencil badge and the rest of the card open the edit window
            ivCowImg.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    listener.onItemClick(item,NONE_TYPE,position,ACTION_SHOW_LARGE_TYPE);
                }
            });
            showImage(item, ivCowImg);

            View.OnClickListener editListener = new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    listener.onItemClick(item,NONE_TYPE,position,ACTION_EDIT_COW_TYPE);
                }
            };
            ivEditCow.setOnClickListener(editListener);
            itemView.setOnClickListener(editListener);
        }

        /**
         * the seen chip and the color of the top band: corpse = no chip and an iron band, seen today = green chip
         * with a check and a turquoise band, otherwise an amber warning chip with the date of the last visit
         */
        private void showSeenAndStrip(CowDetails item, boolean isCorpse){
            if(isCorpse){
                vSeenStrip.setBackgroundColor(ContextCompat.getColor(context, R.color.ww_iron));
                tvLastSeen.setVisibility(View.GONE);
                return;
            }
            tvLastSeen.setVisibility(View.VISIBLE);
            boolean seenToday = false;
            String dateString = null;
            if(item.getLast_seen_at()!=null){
                Calendar date = Calendar.getInstance();
                date.setTimeInMillis(item.getLast_seen_at());
                dateString = getStringFromCalendar(date, "dd/MM/yy", context);
                Calendar cal = Calendar.getInstance();
                seenToday = date.get(Calendar.DAY_OF_MONTH)==cal.get(Calendar.DAY_OF_MONTH)
                        && date.get(Calendar.MONTH)==cal.get(Calendar.MONTH)
                        && date.get(Calendar.YEAR)==cal.get(Calendar.YEAR);
            }
            vSeenStrip.setBackgroundColor(ContextCompat.getColor(context,
                    seenToday ? R.color.ww_turquoise : R.color.ww_brass));
            if(seenToday){
                setChip(tvLastSeen, R.drawable.shape_ww_chip_ok_bg, R.drawable.ic_chip_check,
                        R.color.ww_turquoise_dark, R.string.status_seen_today);
            }else{
                setChip(tvLastSeen, R.drawable.shape_ww_chip_warn_bg, R.drawable.ic_chip_warning,
                        R.color.ww_saddle_dark, 0);
                tvLastSeen.setText(dateString != null
                        ? String.format(context.getString(R.string.status_not_seen_since), dateString)
                        : context.getString(R.string.last_not_seen));
            }
        }

        /**
         * one status chip: background (green / amber / red / neutral), the icon in front and the text color;
         * the icon takes the color of the text. textRes 0 = the caller sets the text
         */
        private void setChip(TextView chip, int backgroundRes, int iconRes, int colorRes, int textRes){
            int color = ContextCompat.getColor(context, colorRes);
            chip.setBackgroundResource(backgroundRes);
            chip.setCompoundDrawablesRelativeWithIntrinsicBounds(iconRes, 0, 0, 0);
            chip.setCompoundDrawableTintList(ColorStateList.valueOf(color));
            chip.setTextColor(color);
            if(textRes != 0){
                chip.setText(textRes);
            }
        }

        /**
         * show items of cows
         * @param item
         * @param ivCowImg
         */
        public void showImage(CowDetails item, ImageView ivCowImg){
            try {
                Glide.with(context)
                        .load(item.getImage_url())
                        //.placeholder(R.drawable.logo_unit_image_placeholder)
                        //.error(R.drawable.logo_unit_image_placeholder)
                        .circleCrop()
                        .into(ivCowImg);
            } catch(Exception e1) {
                e1.printStackTrace();
            }
        }

    }


}
