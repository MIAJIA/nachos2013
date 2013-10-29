package nachos.threads;

import java.util.HashSet;
import java.util.Random;

import nachos.machine.Lib;
import nachos.machine.Machine;

/**
 * A scheduler that chooses threads using a lottery.
 * 
 * <p>
 * A lottery scheduler associates a number of tickets with each thread. When a
 * thread needs to be dequeued, a random lottery is held, among all the tickets
 * of all the threads waiting to be dequeued. The thread that holds the winning
 * ticket is chosen.
 * 
 * <p>
 * Note that a lottery scheduler must be able to handle a lot of tickets
 * (sometimes billions), so it is not acceptable to maintain state for every
 * ticket.
 * 
 * <p>
 * A lottery scheduler must partially solve the priority inversion problem; in
 * particular, tickets must be transferred through locks, and through joins.
 * Unlike a priority scheduler, these tickets add (as opposed to just taking the
 * maximum).
 */
public class LotteryScheduler extends PriorityScheduler {
	/**
	 * Allocate a new lottery scheduler.
	 */
	public LotteryScheduler() {
	}

	/**
	 * Allocate a new lottery thread queue.
	 * 
	 * @param transferPriority
	 *            <tt>true</tt> if this queue should transfer tickets from
	 *            waiting threads to the owning thread.
	 * @return a new lottery thread queue.
	 */
	public ThreadQueue newThreadQueue(boolean transferPriority) {
		return new LotteryQueue(transferPriority);
	}
	
	public int getPriority(KThread thread) {
		Lib.assertTrue(Machine.interrupt().disabled());

		return getThreadState(thread).getPriority();
	}

	public int getEffectivePriority(KThread thread) {
		Lib.assertTrue(Machine.interrupt().disabled());

		return getThreadState(thread).getEffectivePriority();
	}

	public void setPriority(KThread thread, int priority) {
		Lib.assertTrue(Machine.interrupt().disabled());

		Lib.assertTrue(priority >= priorityMinimum
				&& priority <= priorityMaximum);

		getThreadState(thread).setPriority(priority);
	}

	public boolean increasePriority()
	{
		boolean intStatus = Machine.interrupt().disable();

		KThread thread = KThread.currentThread();

		int priority = getPriority(thread);
		if (priority == priorityMaximum)
		{
		  Machine.interrupt().restore(intStatus); // bug identified by Xiao Jia @ 2011-11-04
			return false;
		}

		setPriority(thread, priority + 1);

		Machine.interrupt().restore(intStatus);
		return true;
	}

	public boolean decreasePriority()
	{
		boolean intStatus = Machine.interrupt().disable();

		KThread thread = KThread.currentThread();

		int priority = getPriority(thread);
		if (priority == priorityMinimum)
		{
		  Machine.interrupt().restore(intStatus); // bug identified by Xiao Jia @ 2011-11-04
			return false;
		}

		setPriority(thread, priority - 1);

		Machine.interrupt().restore(intStatus);
		return true;
	}
	
	protected LotteryThreadState getThreadState(KThread thread) {
		if (thread.schedulingState == null)
			thread.schedulingState = new LotteryThreadState(thread);
		return (LotteryThreadState)thread.schedulingState;
	}
	
	public static final int priorityDefault = 1;
	public static final int priorityMinimum = 1;
	public static final int priorityMaximum = Integer.MAX_VALUE;
	
	protected class LotteryQueue extends PriorityScheduler.PriorityQueue {
		LotteryQueue(boolean transferPriority) {
			super(transferPriority);
		}
		
		protected LotteryThreadState pickNextThread() {
			if (waitList.isEmpty()) return null;
			
			int tot = 0;
			int[] sum = new int[waitList.size()];
			
			int i = 0;
			for (KThread thread: waitList) {
				tot += getThreadState(thread).getEffectivePriority();
				sum[i++] = tot;
			}
			
			int now = random.nextInt(tot);
			
			i = 0;
			for (KThread thread : waitList)
				if (now < sum[i++])
					return getThreadState(thread);
			
			return null;
		}
		
		Random random = new Random(25);
	}
	
	protected class LotteryThreadState extends PriorityScheduler.ThreadState {
		public LotteryThreadState(KThread thread) {
			super(thread);
		}
		
		public int getEffectivePriority() {
			if (effectivePriority != expiredPriority) return effectivePriority;
			return getEffectivePriority(new HashSet<LotteryThreadState>());
		}

		private int getEffectivePriority(HashSet<LotteryThreadState> set) {
			if (set.contains(this)) {
				System.out.println("DeadLock!!");
				return priority;
			}
			
			effectivePriority = priority;
			for (PriorityQueue q : donationList) {
				if (q.transferPriority)
					for (KThread t : q.waitList) {
						set.add(this);
						effectivePriority += getThreadState(t).getEffectivePriority(set);
						set.remove(this);
					}
			}
			
			//Also need to check current thread's joinQueue;
			PriorityQueue q = (PriorityQueue) thread.joinQueue;
			for (KThread t : q.waitList) {
				set.add(this);
				effectivePriority += getThreadState(t).getEffectivePriority(set);
				set.remove(this);
			}
			
			return effectivePriority;
		}
	}
}
