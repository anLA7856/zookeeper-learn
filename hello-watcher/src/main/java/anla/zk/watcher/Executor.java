package anla.zk.watcher;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.Watcher;
import org.apache.zookeeper.ZooKeeper;

/**
 *
 *
 * 1. 参数里面输入 localhost:2181 /watch data/znode-data scripts/seq.sh
 * 2. 连接zkclient中，执行 create /watch hi
 * 收znode数据变化通知的。
 * @author luoan
 * @version 1.0
 * @date 2020/10/24 17:51
 **/
public class Executor implements Watcher, Runnable, DataMonitor.DataMonitorListener {

    private String znode;
    private DataMonitor dm;
    private ZooKeeper zk;
    private String pathname;
    private String exec[];
    private Process child;

    public Executor(String hostPort, String znode, String filename, String exec[])
        throws KeeperException, IOException {
        this.pathname = filename;
        this.exec = exec;
        // 创建zk实例, Executor的process会受到znode的数据变化通知
        zk = new ZooKeeper(hostPort, 3000, this);
        dm = new DataMonitor(zk, znode, this);
    }

    /** @param args */
    public static void main(String[] args) {
        if (args.length < 4) {
            System.err.println("USAGE: Executor hostPort znode pathname program [args ...]");
            System.exit(2);
        }
        String hostPort = args[0];
        String znode = args[1];
        String filename = args[2];
        String exec[] = new String[args.length - 3];
        System.arraycopy(args, 3, exec, 0, exec.length);
        try {
            new Executor(hostPort, znode, filename, exec).run();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 在znode发生任何变化时，这个方法都会被调用
     * Watcher
     * @param event
     */
    @Override
    public void process(WatchedEvent event) {
        dm.handle(event);
    }

    /**
     * Runnable
     */
    @Override
    public void run() {
        try {
            synchronized (this) {
                while (!dm.dead) {
                    wait();
                }
            }
        } catch (InterruptedException e) {
        }
    }

    /**
     * DataMonitor.DataMonitorListener
     * @param rc the ZooKeeper reason code
     */
    @Override
    public void closing(int rc) {
        synchronized (this) {
            notifyAll();
        }
    }

    /**
     * 执行业务逻辑的监听器
     * DataMonitor.DataMonitorListener
     * @param data
     */
    @Override
    public void exists(byte[] data) {
        if (data == null) {
            if (child != null) {
                System.out.println("Killing handle");
                child.destroy();
                try {
                    child.waitFor();
                } catch (InterruptedException e) {
                }
            }
            child = null;
        } else {
            if (child != null) {
                System.out.println("Stopping child");
                child.destroy();
                try {
                    child.waitFor();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            try {
                FileOutputStream fos = new FileOutputStream(new File(pathname));
                fos.write(data);
                fos.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
            try {
                System.out.println("Starting child");
                // 执行一个命令
                child = Runtime.getRuntime().exec(exec);
                new StreamWriter(child.getInputStream(), System.out);
                new StreamWriter(child.getErrorStream(), System.err);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    static class StreamWriter extends Thread {

        OutputStream os;

        InputStream is;

        StreamWriter(InputStream is, OutputStream os) {
            this.is = is;
            this.os = os;
            start();
        }

        @Override
        public void run() {
            byte b[] = new byte[80];
            int rc;
            try {
                while ((rc = is.read(b)) > 0) {
                    os.write(b, 0, rc);
                }
            } catch (IOException e) {
            }
        }
    }
}

