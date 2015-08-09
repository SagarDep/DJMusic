package com.macernow.djstava.djmusic;

/**
 * Created by djstava on 15/6/18.
 */
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

public class AdapterTextview extends BaseAdapter {
    private static final String TAG = AdapterTextview.class.getSimpleName();
    private String[] category;
    private Context context;
    private LayoutInflater layoutInflater;

    public AdapterTextview(Context context,String[] category) {
        layoutInflater = LayoutInflater.from(context);
        this.category = category;
    }

    @Override
    public int getCount() {
        return this.category.length;
    }

    @Override
    public Object getItem(int position) {
        return null;
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position,View convertView,ViewGroup parent) {
        View view;
        ViewHolder viewHolder;

        if (convertView == null) {
            view = layoutInflater.inflate(R.layout.adapter_textview_item,parent,false);
            viewHolder = new ViewHolder();
            viewHolder.textView = (TextView)view.findViewById(R.id.adapter_item_textview);
            view.setTag(viewHolder);
        }
        else {
            view = convertView;
            viewHolder = (ViewHolder)view.getTag();
        }

        viewHolder.textView.setText(category[position]);

        return view;
    }

    class ViewHolder {
        TextView textView;
    }
}
