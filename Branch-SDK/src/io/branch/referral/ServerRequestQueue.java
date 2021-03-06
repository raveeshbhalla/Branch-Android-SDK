package io.branch.referral;

import java.util.Collections;
import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.NoSuchElementException;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;

public class ServerRequestQueue {
	private static final String PREF_KEY = "BNCServerRequestQueue";
	private static final int MAX_ITEMS = 25;
	private static ServerRequestQueue SharedInstance;	
	private SharedPreferences sharedPref;
    private SharedPreferences.Editor editor;
	private List<ServerRequest> queue;
    private final Object lock = new Object();

    public static ServerRequestQueue getInstance(Context c) {
    	if(SharedInstance == null) {
    		synchronized(ServerRequestQueue.class) {
    			if(SharedInstance == null) {
    				SharedInstance = new ServerRequestQueue(c);
    			}
    		}
    	}
    	return SharedInstance;
    }


    @SuppressLint( "CommitPrefEdits" )
    private ServerRequestQueue (Context c) {
    	sharedPref = c.getSharedPreferences("BNC_Server_Request_Queue", Context.MODE_PRIVATE);
		editor = sharedPref.edit();
		queue = retrieve();
    }
    
    private void persist() {
    	new Thread(new Runnable() {
			@Override
			public void run() {
				JSONArray jsonArr = new JSONArray();
				synchronized(lock) {
                    for (ServerRequest aQueue : queue) {
                        JSONObject json = aQueue.toJSON();
                        if (json != null) {
                            jsonArr.put( json );
                        }
                    }
					
					try {
						editor.putString(PREF_KEY, jsonArr.toString()).commit();
					} catch (ConcurrentModificationException ex) {
						PrefHelper.Debug("Persisting Queue: ", jsonArr.toString());
					} finally {
						try {
							editor.putString(PREF_KEY, jsonArr.toString()).commit();
						} catch (ConcurrentModificationException ignored) {}
					}
				}
			}
		}).start();
    }
    
    private List<ServerRequest> retrieve() {
    	List<ServerRequest> result = Collections.synchronizedList(new LinkedList<ServerRequest>());
    	String jsonStr = sharedPref.getString(PREF_KEY, null);
    	
    	if (jsonStr != null) {
    		try {
    			JSONArray jsonArr = new JSONArray(jsonStr);
    			for (int i = 0; i < jsonArr.length(); i++) {
    				JSONObject json = jsonArr.getJSONObject(i);
    				ServerRequest req = ServerRequest.fromJSON(json);
    				if (req != null) {
    					result.add(req);
    				}
    			}
    		} catch (JSONException ignored) {
    		}
    	}
    	
    	return result;
    }
    
	public int getSize() {
		return queue.size();
	}
	
	public void enqueue(ServerRequest request) {
		if (request != null) {
			queue.add(request);
			if (getSize() >= MAX_ITEMS) {
				queue.remove(1);				
			}
			persist();
		}
	}
    
	public ServerRequest dequeue() {
		ServerRequest req = null;
		try {
			req = queue.remove(0);
			persist();
		} catch (IndexOutOfBoundsException ignored) {
		} catch (NoSuchElementException ignored) {
		}
        return req;
	}

	public ServerRequest peek() {
		ServerRequest req = null;
		try {
			req = queue.get(0);
		} catch (IndexOutOfBoundsException ignored) {
		} catch (NoSuchElementException ignored) {
		}
        return req;
	}
	
	public ServerRequest peekAt(int index) {
		ServerRequest req = null;
		try {
			req = queue.get(index);
		} catch (IndexOutOfBoundsException ignored) {
		} catch (NoSuchElementException ignored) {
		}
        return req;
	}
	
	public void insert(ServerRequest request, int index) {
		try {
			queue.add(index, request);
			persist();
		} catch (IndexOutOfBoundsException ignored) {
		}
	}
	
	public ServerRequest removeAt(int index) {
		ServerRequest req = null;
		try {
			req = queue.remove(index);
			persist();
		} catch (IndexOutOfBoundsException ignored) {
		}
		return req;
	}

	public boolean containsInstallOrOpen() {
		synchronized(lock) {
            for (ServerRequest req : queue) {
                if (req.getTag().equals(BranchRemoteInterface.REQ_TAG_REGISTER_INSTALL) || req.getTag().equals(BranchRemoteInterface.REQ_TAG_REGISTER_OPEN)) {
                    return true;
                }
            }
		}
		return false;
	}
	
	public void moveInstallOrOpenToFront(String tag, int networkCount) {
		synchronized(lock) {
			Iterator<ServerRequest> iter = queue.iterator();
			while (iter.hasNext()) {
				ServerRequest req = iter.next();
				if (req.getTag().equals(BranchRemoteInterface.REQ_TAG_REGISTER_INSTALL) || req.getTag().equals(BranchRemoteInterface.REQ_TAG_REGISTER_OPEN)) {
					if (req.getTag().equals(BranchRemoteInterface.REQ_TAG_REGISTER_INSTALL)) {
						tag = BranchRemoteInterface.REQ_TAG_REGISTER_INSTALL;
					} else {
						tag = BranchRemoteInterface.REQ_TAG_REGISTER_OPEN;
					}
					iter.remove();
					break;
				}
			}
		}
	    
	    ServerRequest req = new ServerRequest(tag);
	    if (networkCount == 0) {
	    	insert(req, 0);
	    } else {
	    	insert(req, 1);
	    }
	}
}
