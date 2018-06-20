package ru.spb.gamma.esealdemo;

import android.graphics.Color;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import ru.spb.gamma.esealdemo.LogFragment.OnListFragmentInteractionListener;

import java.util.List;

/**
 * {@link RecyclerView.Adapter} that can display a {@link DummyItem} and makes a call to the
 * specified {@link OnListFragmentInteractionListener}.
 * TODO: Replace the implementation with code for your data type.
 */
public class LogViewAdapter extends RecyclerView.Adapter<LogViewAdapter.ViewHolder> {

    private final List<LogContent.LogItem> mValues;
    private final OnListFragmentInteractionListener mListener;

    public LogViewAdapter(List<LogContent.LogItem> items, OnListFragmentInteractionListener listener) {
        mValues = items;
        mListener = listener;
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.fragment_logitem, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(final ViewHolder holder, int position) {
        holder.mItem = mValues.get(position);
        holder.mTime.setText(mValues.get(position).time);
        holder.mLogRecord.setText(mValues.get(position).data);
        holder.mLogRecord.setTextColor( (mValues.get(position).type == 0) ? Color.BLUE : Color.MAGENTA);

        holder.mView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (null != mListener) {
                    // Notify the active callbacks interface (the activity, if the
                    // fragment is attached to one) that an item has been selected.
        //            mListener.onListFragmentInteraction(holder.mItem);
                }
            }
        });
    }

    @Override
    public int getItemCount() {
        return mValues.size();
    }

    public class ViewHolder extends RecyclerView.ViewHolder {
        public final View mView;
        private TextView mTime;
        private TextView mLogRecord;
        public LogContent.LogItem mItem;

        public ViewHolder(View view) {
            super(view);
            mView = view;
            mTime = (TextView) view.findViewById(R.id.time);
            mLogRecord = (TextView) view.findViewById(R.id.data);
        }

//        @Override
//        public String toString() {
//            return super.toString() + " '" + mView.getText() + "'";
//        }
    }
}
