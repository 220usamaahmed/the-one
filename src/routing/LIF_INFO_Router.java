/* 
 * Copyright 2013.7.20, edit by Li LIU. 
 */
/**
 * Implementation of Spray and wait router as depicted in <I>Spray and Wait: An
 * Efficient Routing Scheme for Intermittently Connected Mobile Networks</I> by
 * Thrasyvoulos Spyropoulus et al.
 * 
 */

package routing;

import java.util.LinkedHashMap;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.List;

import core.Message;
import core.DTNHost;
import core.Connection;
import core.SimClock;
import core.Settings;

import util.Tuple;

public class LIF_INFO_Router extends ActiveRouter {
	/** identifier for the initial number of copies setting ({@value} ) */
	public static final String NROF_COPIES = "nrofCopies";
	/** identifier for the binary-mode setting ({@value} ) */
	// public static final String BINARY_MODE = "binaryMode";
	/** LoInF router's settings name space ({@value} ) */
	public static final String LoInF_NS = "LIF_INFO_Router";
	/** Message property key */
	public static final String MSG_COUNT_PROPERTY = LoInF_NS + "." + "copies";
	/** the number of interests **/
	public static final String NROF_INTERESTS = "nofinterests";
	public static final String Max_FRIENDS = "maxOfFriend";
	public static final String P_LOC = "pLoc";
	public static final String P_INS = "pIns";
	public static final String P_FRI = "pFri";
	public static final String BETA_S = "beta";

	public static final double DEF_LOC = 0.3;
	public static final double DEF_INS = 0.3;
	public static final double DEF_FRI = 0.4;
	public static final double DEF_BETA = 0.25;

	protected int initialNrofCopies;
	protected int nofIns;
	protected int maxFri;
	protected double p_loc;
	protected double p_ins;
	protected double p_fri;
	private double beta;
	// protected boolean isBinary;
	public static final String Array_N="N";
	public static final int Default_N = 78;
	private int N;
	private int hour_period=12;
	private int length=5;
	

	// add by Li liu for LoInF
	private LinkedHashMap<Integer, Integer> Locations;
    private HashMap<Integer, int[][]> Loc_adj;
	
    private ArrayList<Integer> Interests = new ArrayList<Integer>();
	private HashMap<Integer, HashMap<Integer,Integer>> pass_Ins;

	private ArrayList<Integer> Friends = new ArrayList<Integer>();	
	private HashMap<Integer, HashMap<Integer,Integer>> pass_Friends;

	public LIF_INFO_Router(Settings s) {
		super(s);
		Settings LIF_settings = new Settings(LoInF_NS);

		initialNrofCopies = LIF_settings.getInt(NROF_COPIES);
		nofIns = LIF_settings.getInt(NROF_INTERESTS);
		maxFri = LIF_settings.getInt(Max_FRIENDS);

		this.Locations = new LinkedHashMap<Integer, Integer>();
		this.Loc_adj=new HashMap<Integer,int[][]>();
		
		if (LIF_settings.contains(P_LOC)) {
			p_loc = LIF_settings.getDouble(P_LOC);
		} else {
			p_loc = DEF_LOC;
		}
		if (LIF_settings.contains(P_INS)) {
			p_ins = LIF_settings.getDouble(P_INS);
		} else {
			p_ins = DEF_INS;
		}
		if (LIF_settings.contains(P_FRI)) {
			p_fri = LIF_settings.getDouble(P_FRI);
		} else {
			p_fri = DEF_FRI;
		}
		if (LIF_settings.contains(BETA_S)) {
			beta = LIF_settings.getDouble(BETA_S);
		} else {
			beta = DEF_BETA;
		}

	}

	/**
	 * Copy constructor.
	 * 
	 * @param r
	 *            The router prototype where setting values are copied from
	 */
	protected LIF_INFO_Router(LIF_INFO_Router r) {
		super(r);
		this.initialNrofCopies = r.initialNrofCopies;
		this.p_loc = r.p_loc;
		this.p_ins = r.p_ins;
		this.p_fri = r.p_fri;
		this.beta = r.beta;
		this.maxFri = r.maxFri;
		this.nofIns = r.nofIns;

		
		
		this.Locations = r.Locations;
		this.Loc_adj=r.Loc_adj;

		this.Friends = r.Friends;
		this.pass_Friends=r.pass_Friends;
		
		this.Interests = r.Interests;
        this.pass_Ins=r.pass_Ins;
	}

	@Override
	public int receiveMessage(Message m, DTNHost from) {
		return super.receiveMessage(m, from);
	}

	@Override
	public Message messageTransferred(String id, DTNHost from) {
		Message msg = super.messageTransferred(id, from);
		
		Integer nrofCopies = (Integer) msg.getProperty(MSG_COUNT_PROPERTY);

		assert nrofCopies != null : "Not a SnW message: " + msg;
        if (nrofCopies>1)
        {
        	nrofCopies = (int) Math.ceil(nrofCopies / 2.0);
        }
        msg.updateProperty(MSG_COUNT_PROPERTY, nrofCopies);
		
		return msg;
 	
	}

	@Override
	public boolean createNewMessage(Message msg) {
		makeRoomForNewMessage(msg.getSize());

		msg.setTtl(this.msgTtl);
		msg.addProperty(MSG_COUNT_PROPERTY, new Integer(initialNrofCopies));
		addToMessages(msg, true);
		return true;
	}
	
	/**
	 * Called just before a transfer is finalized (by 
	 * {@link ActiveRouter#update()}).
	 * Reduces the number of copies we have left for a message. 
	 * In binary Spray and Wait, sending host is left with floor(n/2) copies,
	 * but in standard mode, nrof copies left is reduced by one. 
	 */
	@Override
	protected void transferDone(Connection con) {
		Integer nrofCopies;
		String msgId = con.getMessage().getId();
		/* get this router's copy of the message */
		Message msg = getMessage(msgId);

		if (msg == null) { // message has been dropped from the buffer after..
			return; // ..start of transfer -> no need to reduce amount of copies
		}
		
		/* reduce the amount of copies left */
		nrofCopies = (Integer)msg.getProperty(MSG_COUNT_PROPERTY);
		if (nrofCopies==1)
		{
			this.deleteMessage(msgId, false);
		}
		else
		{
			nrofCopies /= 2;
			msg.updateProperty(MSG_COUNT_PROPERTY, nrofCopies);
		}
	}
	
	@Override
	public void changedConnection(Connection con) {
		if (con.isUp()) {
			
			LIF_INFO_Router thisRouter = (LIF_INFO_Router) this.getHost().getRouter();
			DTNHost otherHost = con.getOtherNode(getHost());
			LIF_INFO_Router othRouter = (LIF_INFO_Router) otherHost.getRouter();
			
			updateContactFor(otherHost);
			
			int hour=((int)SimClock.getIntTime()/3600+1)%hour_period;
			thisRouter.set_pass_Ins(this.update_Ins(thisRouter,othRouter,hour));
			thisRouter.set_pass_Friends(this.update_pass_Friend(thisRouter, othRouter, hour));
		}
	}
	
	@Override
	public void update() {
		super.update();
		//update_InsAndFri();
		//System.out.println(SimClock.getIntTime());
		if (!canStartTransfer() || isTransferring()) {
			return; // nothing to transfer or is currently transferring
		}

		// try messages that could be delivered to final recipient
		if (exchangeDeliverableMessages() != null) {
			return;
		}

		tryOtherMessages();
	}

	/**
	 * Tries to send all other messages to all connected hosts ordered by their
	 * delivery probability
	 * 
	 * @return The return value of {@link #tryMessagesForConnected(List)}
	 */
	private Tuple<Message, Connection> tryOtherMessages() {
		List<Tuple<Message, Connection>> messages = new ArrayList<Tuple<Message, Connection>>();
	/*
		 * for all connected hosts collect all messages that have a higher
		 * probability of delivery by the other host
		 */

		for (Connection con : getConnections()) {
			DTNHost other = con.getOtherNode(getHost());
			
			LIF_INFO_Router othRouter = (LIF_INFO_Router) other.getRouter();

			if (othRouter.isTransferring()) {
				continue; // skip hosts that are transferring
			}
			//List<Message> copies = sortByQueueMode(getMessagesWithCopiesLeft(2));
			List<Message> copies = getMessagesWithCopiesLeft(2);
			
			for (Message m : copies) {
				if (othRouter.hasMessage(m.getId())) {
					continue; // skip messages that the other one has
				}
				DTNHost m_to=m.getTo();
				LIF_INFO_Router mto_router = (LIF_INFO_Router) m_to.getRouter();
				
				if ((simCopy(this,othRouter,mto_router)+0.1)>0)				
				{
					messages.add(new Tuple<Message, Connection>(m, con));
					//System.out.println("Copies:"+m.getId()+" size:"+m.getSize());
				}
			
			}
          
			//List<Message> copyOne = sortByQueueMode(getMessagesWithCopiesLeft(1));
			List<Message> copyOne = getMessagesWithCopiesLeft(1);

			for (Message m : copyOne) {
				if (othRouter.hasMessage(m.getId())) {
					continue; // skip messages that the other one has
				}
				DTNHost m_to=m.getTo();
				LIF_INFO_Router mto_router = (LIF_INFO_Router) m_to.getRouter();
				
				if (simCopy(this,othRouter,mto_router)>0)
				{
					messages.add(new Tuple<Message, Connection>(m, con));
					
				}
				
			}

		}

		if (messages.size() == 0) {
			return null;
		}

		// sort the message-connection tuples

		return tryMessagesForConnected(messages); // try to send messages
	}

	/**
	 * Creates and returns a list of messages this router is currently carrying
	 * and still has copies left to distribute (nrof copies > 1).
	 * 
	 * @return A list of messages that have copies left
	 */
	protected List<Message> getMessagesWithCopiesLeft(int i) {
		List<Message> list1 = new ArrayList<Message>();
		List<Message> list2 = new ArrayList<Message>();

		for (Message m : getMessageCollection()) {
			Integer nrofCopies = (Integer) m.getProperty(MSG_COUNT_PROPERTY);
			assert nrofCopies != null : "SnW message " + m + " didn't have "
					+ "nrof copies property!";
			if (nrofCopies > 1) {
				list1.add(m);
			}
			if (nrofCopies == 1) {
				list2.add(m);
			}
		}
		if (i == 1) {
			return list2;
		} else {
			return list1;
		}
	}

	@Override
	public LIF_INFO_Router replicate() {
		return new LIF_INFO_Router(this);
	}

	// // add by Li Liu for LoInF
	// public void initData(int address) { // load the location, interest and
	// 									// friends
	// 	String id = "" + address;
	// 	this.Locations = new LinkedHashMap<Integer, Integer>();
	// 	this.Loc_adj=new HashMap<Integer,int[][]>();
	// 	this.Friends= DataUtil1.getAllFriends(id);
	// 	this.Interests = DataUtil1.getAllInsts(id);
	
	// }

	private void updateContactFor(DTNHost host) {
		//update the contact list;
		int k=1;
		int hostid=host.getAddress();
		if (this.Locations.containsKey(hostid))
			k=k+Locations.get(hostid);
		Locations.put(hostid,k);
		
		//update ego network
		int hour=((int)SimClock.getIntTime()/3600+1)%hour_period;
		LIF_INFO_Router otherRouter=(LIF_INFO_Router)host.getRouter();
		int idx=host.getAddress();
		int idy;
		int value;
		
		if (!this.Loc_adj.containsKey(hour))
		{
			int[][] Adj=new int[N][N];
			for (int i=0;i<N;i++)
				for(int j=0;j<N;j++)
					Adj[i][j]=0;
			this.Loc_adj.put(hour,Adj);
		}
		else
		{
			int [][] Adj=this.Loc_adj.get(hour);
			for (int m=0;m<N;m++)
			{
				if(otherRouter.Locations.containsKey(m))
				{
					idy=m;
					value=otherRouter.Locations.get(m);
					Adj[idx][idy]=value;
					Adj[idy][idx]=value;
				}
			
			}
			this.Loc_adj.put(hour,Adj);
		}
		
	}
	
	
	public void update_Location()
	{
		int time = ((int)SimClock.getTime())/3600;
		if (time==0)
			this.Locations.clear();
	}
	
	public void update_InsAndFri() {
		
		String id = "" + this.getHost().getAddress();
		double time = SimClock.getTime();
		//DataUtil.updateInst(id, time);
		//DataUtil.updateFriend(id, time);
	}

	public LinkedHashMap<Integer, Integer> get_Location() {
		return this.Locations;
	}

	public ArrayList<Integer> get_Interests() {
		return this.Interests;
	}

	public ArrayList<Integer> get_Friends() {
		return this.Friends;
	}
	public HashMap<Integer,HashMap<Integer,Integer>> get_pass_Friends() {
		if (this.pass_Friends==null)
		{
			return new HashMap<Integer,HashMap<Integer,Integer>>();
		}
		return this.pass_Friends;
	}
	public void set_pass_Friends(HashMap<Integer,HashMap<Integer,Integer>> fri1) {
		if (fri1!=null)
		{
			this.pass_Friends=fri1;
		}
		
		
	}
	
	public HashMap<Integer,HashMap<Integer,Integer>> get_pass_Ins() {
		if (this.pass_Ins==null)
		{
			return new HashMap<Integer,HashMap<Integer,Integer>>();
		}
		return this.pass_Ins;
	}
	
	public void set_pass_Ins(HashMap<Integer,HashMap<Integer,Integer>> ins1) {
		if(ins1!=null)
		{
			this.pass_Ins=ins1;
		}
	
	}

    public HashMap<Integer,HashMap<Integer,Integer>> update_Ins(LIF_INFO_Router loinf1,LIF_INFO_Router loinf2, int hour)
    {
    	//ArrayList<Integer> Ins1 = loinf1.get_Interests();
		ArrayList<Integer> Ins2 = loinf2.get_Interests();
		
		HashMap<Integer,HashMap<Integer,Integer>> pass_Ins1=loinf1.get_pass_Ins();
		//System.out.println(Ins2);
		
		if (Ins2.size()!=0)
		{
			for (int i=0;i<Ins2.size();i++)
			{
				Integer data=Ins2.get(i).intValue();
				if (pass_Ins1==null)
				{
					pass_Ins1=new HashMap<Integer,HashMap<Integer,Integer>>();
					HashMap<Integer,Integer> Ins_data=new HashMap<Integer,Integer>();
   				    Ins_data.put(data, 1);
   				    pass_Ins1.put(hour,Ins_data);   				    
				}
				else
				{
					HashMap<Integer,Integer> Ins_data=pass_Ins1.get(hour);
					if (Ins_data==null)
					{
						Ins_data=new HashMap<Integer,Integer>();
						Ins_data.put(data, 1);
						pass_Ins1.put(hour,Ins_data);
					}
					else
					{
						Integer number=Ins_data.get(data);
						if (number==null)
						{
							Ins_data.put(data, 1);							
						}
						else
						{
							Ins_data.put(data, number+1);						
						}
					}
				}
			}
			
			
			return pass_Ins1;
			
		}
		return null;
 	
    }
    
	public HashMap<Integer,HashMap<Integer,Integer>> update_pass_Friend(LIF_INFO_Router loinf1, LIF_INFO_Router loinf2, int hour)
	{
		
		ArrayList<Integer> fri2=loinf2.get_Friends();
		HashMap<Integer,HashMap<Integer,Integer>> pass_fri1=loinf1.get_pass_Friends();
		//System.out.println(fri2);
		
		if (fri2.size()!=0)
		{
			for (int i=0;i<fri2.size();i++)
			{
				Integer data=fri2.get(i).intValue();
				if (pass_fri1==null)
				{
					pass_fri1=new HashMap<Integer,HashMap<Integer,Integer>>();
					HashMap<Integer,Integer> fri_data=new HashMap<Integer,Integer>();
   				    fri_data.put(data, 1);
   				    pass_fri1.put(hour,fri_data);
   				    
				}
				else
				{
					HashMap<Integer,Integer> fri_data=pass_fri1.get(hour);
					if (fri_data==null)
					{
					    fri_data=new HashMap<Integer,Integer>();
						fri_data.put(data, 1);
						pass_fri1.put(hour,fri_data);
					}
					else
					{
						
						fri_data.put(data, 1);	
					}
				}
			}
			
			return pass_fri1;
		}
		
		return null;	

}
	 public double simLoc(LIF_INFO_Router loinf1,LIF_INFO_Router loinf2, double time) {
			int host =loinf1.getHost().getAddress();
			int des_host=loinf2.getHost().getAddress();
		    int hour = (int) (time / 3600 )%hour_period;
		    
			double similar = 0,sim=0;
			double a=1;
			for (int j=hour;j<hour+ length;j++)
			{
			 a=a*0.8;	
			 int[][] Adj=this.Loc_adj.get(j%hour_period);
			 similar=0;
			if (Adj!=null)
			{
			for (int i=0;i<N;i++)
				if (Adj[host][i]!=0&&Adj[des_host][i]!=0)
					similar++;
			}
			
			sim=sim+similar*a;
			
			}
			
			return sim;
	    
		}
	    
		public double simIns(LIF_INFO_Router loinf1,LIF_INFO_Router loinf2,double time) 
		{
			int hour=((int)time/3600+1)%hour_period;
			//ArrayList<Integer> Ins1 = loinf1.get_Interests();
			ArrayList<Integer> Ins2 = loinf2.get_Interests();
					
			HashMap<Integer,HashMap<Integer,Integer>> pass_Ins1=loinf1.get_pass_Ins();
			if (pass_Ins1.isEmpty())
			{
				return 0;
			}

			
			double sum=0,number;
			double uni_sum=0;
			double a1=1;
			
			for (int k=hour;k<hour+length;k++)
			{
				uni_sum=0;
				a1=a1*0.8;
				HashMap<Integer,Integer> data_ins1=pass_Ins1.get(k%hour_period);
			    if (data_ins1!=null)
			    {
			    	if (Ins2.size()!=0)
					{
						for (int i=0;i<Ins2.size();i++)
						{
							number=0;
							Integer data=Ins2.get(i).intValue();
							if (data_ins1.get(data)!=null)
							{
								number=data_ins1.get(data).intValue();
							}
							
							uni_sum=uni_sum+number;
							
						}
					}
			    }
			    sum=sum+uni_sum*a1;
			   
			}
			
			return sum;
	    }
		
		
	
	public double simFriends(LIF_INFO_Router loinf1,LIF_INFO_Router loinf2, double time) {
		int hour=((int)time/3600+1)%hour_period;
		
		ArrayList<Integer> fri2 = loinf2.get_Friends();
		double sum=0;		
		double uni_sum=0;
		double a1=1;
		
		
		for (int k=hour;k<hour+length;k++)
		{
			HashMap<Integer,HashMap<Integer,Integer>> pass_fri1=loinf1.get_pass_Friends();
		    if(pass_fri1.isEmpty())
		    	return 0;
		    uni_sum=0;
		    HashMap<Integer,Integer> data_fri1=pass_fri1.get(k%hour_period);
			if (data_fri1!=null)
			{
				
				if (fri2.size()!=0)
				{
					for (int i=0;i<fri2.size();i++)
					{
						
						Integer data=fri2.get(i).intValue();
						if (data_fri1.containsKey(data))
							uni_sum++;
					}
				}		
				
						
			}
			sum=sum+uni_sum*a1;
			a1=a1*0.8;
			}
		// System.out.println("sim_fri= "+sum);
	return sum;
		
	}
    public double maxfun(double f1,double f2)
    {
    	if (f1>f2)
    		return f1;
    	else
    		return f2;
    	
    }
	public double simCopy(LIF_INFO_Router loinf1, LIF_INFO_Router loinf2,LIF_INFO_Router dest) {
		double time = SimClock.getTime();
			
		double this_loc=0;
		double other_loc=0;
		
		this_loc=simLoc(loinf1,dest,time);
		other_loc=simLoc(loinf2,dest,time);
		
		double sum_loc=this_loc+other_loc;
		double this_simloc,other_simloc;
		if (sum_loc==0)
		{
			this_simloc=0;
			other_simloc=0;
		}
		else
		{
			this_simloc=this_loc/sum_loc;
			other_simloc=other_loc/sum_loc;
		}
		
		double this_ins=simIns(loinf1,dest,time);
		double other_ins=simIns(loinf2,dest,time);
		
		double sum_ins=this_ins+other_ins;
		double this_simins,other_simins;
		if (sum_ins==0)
		{
			this_simins=0;
			other_simins=0;
		}
		else
		{
			this_simins=this_ins/sum_ins;
			other_simins=other_ins/sum_ins;
		}
		
		double this_fri=simFriends(loinf1,dest,time);
		double other_fri=simFriends(loinf2,dest,time);
		
		double sum_fri=this_fri+other_fri;
		double this_simfri=0,other_simfri=0;
		if (sum_fri!=0)
		{
			this_simfri=this_fri/sum_fri;
			other_simfri=other_fri/sum_fri;
		}

		double fun_sim1= p_loc * this_simloc + p_ins * this_simins + p_fri *this_simfri ;
		double fun_sim2= p_loc * other_simloc + p_ins * other_simins + p_fri *other_simfri ;

		double fun_simloc=other_simloc-this_simloc;
		double fun_simins=other_simins-this_simins;
		double fun_simfri=other_simfri-this_simfri;
		double fun_sim=p_loc*(other_simloc-this_simloc)+p_ins*(other_simins-this_simins)+p_fri*(other_simfri-this_simfri);
		//System.out.println("sim= "+(fun_sim));
		//return fun_sim2-fun_sim1;
		
		return fun_sim;
	}
	
	
}	
	

	