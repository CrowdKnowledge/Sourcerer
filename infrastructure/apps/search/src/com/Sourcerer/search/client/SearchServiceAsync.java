package com.Sourcerer.search.client;

import com.google.gwt.user.client.rpc.AsyncCallback;

public interface SearchServiceAsync {
	
	public void update(String text, AsyncCallback<String> callback);
}