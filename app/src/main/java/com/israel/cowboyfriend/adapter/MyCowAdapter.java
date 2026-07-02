package com.israel.cowboyfriend.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.israel.cowboyfriend.R;
import com.israel.cowboyfriend.classes.CowDetails;
import com.israel.cowboyfriend.interfaces.InterOnItemClickListener;

import java.util.ArrayList;


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
        holder.bind(cows.get(position), listener);
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

        TextView tvCalfName ;
        TextView tvMonNum ;
        TextView tvGender ;
        ImageView ivCowImg;
        TextView tvComment;
        Context context;
        CheckBox cbCorpse;
        Button btnSave;

        public MyViewHolder(@NonNull View itemView, Context context) {
            super(itemView);
            tvCalfName = itemView.findViewById(R.id.tvCowNum);
            tvMonNum = itemView.findViewById(R.id.tvMonNum);
            tvGender  = itemView.findViewById(R.id.tvGender);
            ivCowImg = itemView.findViewById(R.id.ivCowImg);
            tvComment = itemView.findViewById(R.id.tvComment);
            cbCorpse = itemView.findViewById(R.id.cbCorpse);
            btnSave = itemView.findViewById(R.id.btnSave);
            this.context = context;
        }

        public void bind(final CowDetails item, final InterOnItemClickListener listener) {
            tvCalfName.setText(String.format(context.getString(R.string.title_calf_number), item.getNumber()+""));
            tvMonNum.setText(String.format(context.getString(R.string.title_mom_number), item.getNumber_mom()+""));
            tvGender.setText(String.format(context.getString(R.string.title_calf_gender), item.getGender()));
            if(item.getComment()!=null) {
                tvComment.setText(String.format(context.getString(R.string.title_comment), item.getComment()));
            }else {
                tvComment.setText(String.format(context.getString(R.string.title_comment), context.getString(R.string.no_comment)));
            }
            showImage(item, ivCowImg);
            cbCorpse.setChecked(item.isCorpse());
            btnSave.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    item.setCorpse(cbCorpse.isChecked());
                    listener.onItemClick(item);
                }
            });
//            cbCorpse.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
//                @Override
//                public void onCheckedChanged(@NonNull CompoundButton compoundButton, boolean result) {
//                    Log.d("testCorpse", "onCheckedChanged:"+result);
//
//                    //item.setCorpse(result);
//                    //btnSave.setVisibility(View.VISIBLE);
//
//                }
//            });
//            itemView.setOnClickListener(new View.OnClickListener() {
//                @Override public void onClick(View v) {
//                    //listener.onItemClick(item);
//                }
//            });
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
