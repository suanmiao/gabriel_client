package edu.cmu.cs.gabriel;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by suanmiao on 01/12/2016.
 */

public class SListAdapter extends BaseAdapter {

  private List<Model> modelList;
  private Context mContext;

  public SListAdapter(Context context) {
    super();
    modelList = new ArrayList<Model>();
    this.mContext = context;
  }

  @Override public int getCount() {
    return modelList.size();
  }

  @Override public Object getItem(int position) {
    return modelList.get(position);
  }

  @Override public long getItemId(int position) {
    return position;
  }

  public void addItem(Model model){
    this.modelList.add(model);
    notifyDataSetChanged();
  }

  public void clearData(){
    this.modelList.clear();
  }

  @Override public View getView(int position, View convertView, ViewGroup parent) {
    View v = convertView;
    if (v == null) {
      LayoutInflater vi;
      vi = LayoutInflater.from(mContext);
      v = vi.inflate(R.layout.item_conversation, null);
    }

    Model model = (Model)getItem(position);

    if (model != null) {
      TextView textView = (TextView) v.findViewById(R.id.item_text);

      if (textView != null) {
        textView.setText(model.text);
      }
    }
    return v;
  }

  public static class Model {
    public String text;

    public Model(String text) {
      this.text = text;
    }
  }
}
