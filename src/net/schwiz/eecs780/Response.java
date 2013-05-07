package net.schwiz.eecs780;

import java.util.ArrayList;

import com.google.gson.Gson;

public class Response {


	private String action;
	private ArrayList<String> list;
	private String name;
	private long last_update;
	
	public Response(){}
	
	public ArrayList<String> getList() {
		return list;
	}
	public void setList(ArrayList<String> list) {
		this.list = list;
	}
	public long getLast_update() {
		return last_update;
	}
	public void setLast_update(long last_update) {
		this.last_update = last_update;
	}
	
	public static Response deserializeList(String json){
		return new Gson().fromJson(json, Response.class);
	}

	public String getAction() {
		return action;
	}

	public void setAction(String action) {
		this.action = action;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	@Override
	public String toString() {
		return new Gson().toJson(this);
	}
	
}
