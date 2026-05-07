package com.israel.cowboyfriend.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
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
        Context context;

        public MyViewHolder(@NonNull View itemView, Context context) {
            super(itemView);
            tvCalfName = itemView.findViewById(R.id.tvCowNum);
            tvMonNum = itemView.findViewById(R.id.tvMonNum);
            tvGender  = itemView.findViewById(R.id.tvGender);
            ivCowImg = itemView.findViewById(R.id.ivCowImg);
            this.context = context;
        }

        public void bind(final CowDetails item, final InterOnItemClickListener listener) {
            tvCalfName.setText(item.getNumber()+"");
            tvMonNum.setText(item.getNumber_mom()+"");
            tvGender.setText(item.getGender());
            //ivUserImg.setImageBitmap(item.getBitmap());
            //ivUserImg.setImageResource(item.getDrawable());
            showImage(item, ivCowImg);
            itemView.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    listener.onItemClick(item);
                }
            });
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
