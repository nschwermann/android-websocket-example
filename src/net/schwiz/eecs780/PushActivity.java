package net.schwiz.eecs780;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;

import com.codebutler.android_websockets.WebSocketClient.Listener;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.ListFragment;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.ActionMode;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.BaseAdapter;
import android.widget.CheckedTextView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

public class PushActivity extends Activity {

	private static final int MENU_ADD = Menu.FIRST + 1000;
	private static final int ACTION_DELETE = Menu.FIRST + 50;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		if(savedInstanceState == null) {
			FragmentMyList list = new FragmentMyList();
			getFragmentManager().beginTransaction().add(android.R.id.content, list, FragmentMyList.class.getName()).commit();
		}
	}
	
	public static class FragmentMyList extends ListFragment implements PushService.PushListener{
		
		private int mSelection = -1;
		private ActionMode mActionMode;
		private ArrayList<String> mSavedList;
		
		private PushService mService;
		
		private ServiceConnection mConnection = new ServiceConnection() {
			
			@Override
			public void onServiceDisconnected(ComponentName name) {
				mService = null;
			}
			
			@Override
			public void onServiceConnected(ComponentName name, IBinder service) {
				mService = ((PushService.Binder)service).getService();
				mService.onStartCommand(null, 0, 0);
				mSavedList = mService.getList();
				mService.setListener(FragmentMyList.this);
				((ArrayAdapter<String>)getListAdapter()).clear();
				((ArrayAdapter<String>)getListAdapter()).addAll(mSavedList);
				((ArrayAdapter<String>)getListAdapter()).notifyDataSetInvalidated();
			}
		};
		
		public FragmentMyList(){
		}
		
		@Override
		public void onCreate(Bundle savedInstanceState) {
			super.onCreate(savedInstanceState);
			setHasOptionsMenu(true);
		}
		
		@Override
		public void onPause() {
			super.onPause();
			if(mService != null) mService.setListener(null);
		}
		
		@Override
		public void onResume() {
			super.onResume();
			if(mService != null) {
				mSavedList = mService.getList();
				mService.setListener(this);
				((ArrayAdapter<String>)getListAdapter()).clear();
				((ArrayAdapter<String>)getListAdapter()).addAll(mSavedList);
				((ArrayAdapter<String>)getListAdapter()).notifyDataSetInvalidated();
			}
		}
		
		@Override
		public void onListItemClick(ListView l, View v, int position, long id) {
			if(mSelection == position) return;
			if(mActionMode != null){
				mActionMode.finish();
				mActionMode = null;
			}
			mSelection = position;
			((CheckedTextView)v).setChecked(true);
			getActivity().startActionMode(new ActionModeString());
		}
		
		@Override
		public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
			super.onCreateOptionsMenu(menu, inflater);
			menu.add(3, MENU_ADD, 0, "New Item").setIcon(R.drawable.ic_action_new).setShowAsActionFlags(MenuItem.SHOW_AS_ACTION_IF_ROOM);
		}
		
		@Override
		public boolean onOptionsItemSelected(MenuItem item) {
			if(MENU_ADD == item.getItemId()){
				DialogAddItem dialog = new DialogAddItem();
				dialog.show(getFragmentManager(), DialogAddItem.class.getName());
				return true;
			}
			return super.onOptionsItemSelected(item);
		}
		
		@Override
		public void onActivityCreated(Bundle savedInstanceState) {
			super.onActivityCreated(savedInstanceState);
			getActivity().bindService(PushService.startIntent(getActivity().getApplicationContext()), mConnection, Context.BIND_IMPORTANT);
			if(mSavedList == null)mSavedList = new ArrayList<String>();
			setListAdapter(new ArrayAdapter<String>(getActivity(), android.R.layout.simple_list_item_checked));
			((ArrayAdapter<String>)getListAdapter()).addAll(mSavedList);
			((ArrayAdapter<String>)getListAdapter()).notifyDataSetInvalidated();
		}
		
		@Override
		public void onAttach(Activity activity) {
			super.onAttach(activity);
			activity.getApplicationContext().startService(PushService.startIntent(activity.getApplicationContext()));
		}
		
		@Override
		public void onDetach() {
			super.onDetach();
			if(mService != null) mService.setListener(null);
			getActivity().unbindService(mConnection);
		}
		
		@Override
		public void newResponse(Response response) {
			if("add".equals(response.getAction())){
				((ArrayAdapter<String>)getListAdapter()).add(response.getName());
				((ArrayAdapter<String>)getListAdapter()).notifyDataSetChanged();
			}
			else if("delete".equals(response.getAction())){
				((ArrayAdapter<String>)getListAdapter()).remove(response.getName());
				((ArrayAdapter<String>)getListAdapter()).notifyDataSetChanged();
			}
			else if("connect".equals(response.getAction())){
				((ArrayAdapter<String>)getListAdapter()).clear();
				((ArrayAdapter<String>)getListAdapter()).addAll(response.getList());
				((ArrayAdapter<String>)getListAdapter()).notifyDataSetInvalidated();
			}
		}
		
		private final class ActionModeString implements ActionMode.Callback{

			@Override
			public boolean onCreateActionMode(ActionMode mode, Menu menu) {
				mActionMode = mode;
				setMenu(mode, menu);
				return true;
			}

			@Override
			public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
				return false;
			}

			@Override
			public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
				if(item.getItemId() == ACTION_DELETE){
					if(mService != null && mService.isConnected()){
						String string = ((ArrayAdapter<String>)getListAdapter()).getItem(mSelection);
						mService.removeItem(string);
						((ArrayAdapter<String>)getListAdapter()).remove(string);
						((ArrayAdapter<String>) getListAdapter()).notifyDataSetChanged();
						mode.finish();
					}
					else {
						Toast.makeText(getActivity(), "Not connected to push service please try again", Toast.LENGTH_SHORT);
					}
				}
				return true;
			}

			@Override
			public void onDestroyActionMode(ActionMode mode) {
				if(getListView().getChildAt(mSelection) != null){
					((CheckedTextView)getListView().getChildAt(mSelection)).setChecked(false);
				}
				mSelection = -1;
			}
			
			private void setMenu(ActionMode mode, Menu menu){
				menu.add(0, ACTION_DELETE, 2, "Delete").setIcon(R.drawable.ic_action_delete).setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
			}
			
		}
				
		public static class DialogAddItem extends DialogFragment{
			
			FragmentMyList mParent;
			HashSet<String> mSuggestions;
			
			public DialogAddItem() {
			}
			
			@Override
			public void onCreate(Bundle savedInstanceState) {
				super.onCreate(savedInstanceState);
				final String json = PreferenceManager.getDefaultSharedPreferences(getActivity()).getString("gsuggestions", null);
				if(json != null){
					final Gson g = new Gson();
					final Type type = new TypeToken<HashSet<String>>(){}.getType();
					mSuggestions = g.fromJson(json, type);
				}
				if(mSuggestions == null){
					mSuggestions = new HashSet<String>();
				}
			}
			
			@Override
			public void onActivityCreated(Bundle arg0) {
				super.onActivityCreated(arg0);
				mParent = (FragmentMyList) getActivity().getFragmentManager().findFragmentByTag(FragmentMyList.class.getName());
			}
			
			@Override
			public void onSaveInstanceState(Bundle arg0) {
				super.onSaveInstanceState(arg0);
				arg0.putString("text", ((AutoCompleteTextView)getDialog().findViewById(R.id.suggestion_text)).getText().toString());
			}
			
			@Override
			public Dialog onCreateDialog(Bundle savedInstanceState) {
				final AutoCompleteTextView text = (AutoCompleteTextView) View.inflate(getActivity(), R.layout.suggest_text, null);
				final ArrayAdapter<String> adapter = new ArrayAdapter<String>(getActivity(), android.R.layout.simple_dropdown_item_1line);
				Iterator<String> iterator = mSuggestions.iterator();
				while(iterator.hasNext()){
					adapter.add(iterator.next());
				}
				text.setAdapter(adapter);
				final AlertDialog.Builder builder = new Builder(getActivity());
				builder.setTitle("Add Item");
				builder.setView(text);
				builder.setPositiveButton("OK", new OnClickListener() {
					
					@Override
					public void onClick(DialogInterface dialog, int which) {
						if(text.getText().length() > 0){
							mSuggestions.add(text.getText().toString());
							final String item = text.getText().toString();
							if(mParent.mService != null && mParent.mService.isConnected()){
								mParent.mService.addItem(item);
								((ArrayAdapter<String>)mParent.getListAdapter()).add(item);
								((ArrayAdapter<String>)mParent.getListAdapter()).notifyDataSetChanged();
								dialog.dismiss();
							}
							else {
								Toast.makeText(getActivity(), "Not connected to push service please try again", Toast.LENGTH_SHORT);
							}
						}
					}
				});
				builder.setNegativeButton("Cancel", new OnClickListener() {
					
					@Override
					public void onClick(DialogInterface dialog, int which) {
						dialog.dismiss();
					}
				});
				text.requestFocus();
				AlertDialog dialog = builder.create();
				dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
				return dialog;
			}
			
		}

	}
}
