package anla.zk.watcher;


import java.util.Arrays;
import org.apache.zookeeper.AsyncCallback.StatCallback;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.KeeperException.Code;
import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.Watcher;
import org.apache.zookeeper.ZooKeeper;
import org.apache.zookeeper.data.Stat;


/**
 * @author luoan
 * @version 1.0
 * @date 2020/10/24 17:51
 **/
public class DataMonitor implements StatCallback {

    private ZooKeeper zk;
    private String znode;
    boolean dead;
    private DataMonitorListener listener;
    private byte[] prevData;

    public DataMonitor(ZooKeeper zk, String znode, DataMonitorListener listener) {
        this.zk = zk;
        this.znode = znode;
        this.listener = listener;
        // Get things started by checking if the node exists. We are going
        // to be completely event driven
        zk.exists(znode, true, this, null);
    }

    public void handle(WatchedEvent event) {
        String path = event.getPath();
        if (event.getType() == Watcher.Event.EventType.None) {
            // We are are being told that the state of the
            // connection has changed
            switch (event.getState()) {
                case SyncConnected:
                    // In this particular example we don't need to do anything
                    // here - watches are automatically re-registered with
                    // server and any watches triggered while the client was
                    // disconnected will be delivered (in order of course)
                    break;
                case Expired:
                    // It's all over
                    dead = true;
                    listener.closing(KeeperException.Code.SessionExpired);
                    break;
            }
        } else {
            if (path != null && path.equals(znode)) {
                // Something has changed on the node, let's find out
                zk.exists(znode, true, this, null);
            }
        }
    }

    /**
     * StatCallback
     * @param rc
     * @param path
     * @param ctx
     * @param stat
     */
    @Override
    public void processResult(int rc, String path, Object ctx, Stat stat) {
        boolean exists;
        switch (rc) {
            case Code.Ok:
                exists = true;
                System.out.println("Code.Ok");
                break;
            case Code.NoNode:
                exists = false;
                System.out.println("Code.NoNode");
                break;
            case Code.SessionExpired:
                System.out.println("Code.SessionExpired");
            case Code.NoAuth:
                dead = true;
                System.out.println("Code.NoAuth");
                listener.closing(rc);
                return;
            default:
                // Retry errors
                System.out.println("Retry errors");
                zk.exists(znode, true, this, null);
                return;
        }

        byte b[] = null;
        if (exists) {
            try {
                // 存储当前数据
                b = zk.getData(znode, false, null);
            } catch (KeeperException e) {
                // We don't need to worry about recovering now. The watch
                // callbacks will kick off any exception handling
                e.printStackTrace();
            } catch (InterruptedException e) {
                return;
            }
        }
        // 比较当前数据
        if ((b == null && b != prevData) || (b != null && !Arrays.equals(prevData, b))) {
            System.out.println("替换当前数据");
            listener.exists(b);
            prevData = b;
        }
    }

    /** Other classes use the DataMonitor by implementing this method */
    public interface DataMonitorListener {

        /** The existence status of the node has changed. */
        void exists(byte data[]);

        /**
         * The ZooKeeper session is no longer valid.
         *
         * @param rc the ZooKeeper reason code
         */
        void closing(int rc);
    }
}
