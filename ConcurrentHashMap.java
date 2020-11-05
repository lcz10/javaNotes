package com.lcz.springmvc.servlet.test;

import java.util.concurrent.ConcurrentHashMap;

/**
 * ClassName:HashZ
 * Package:com.lcz.springmvc.servlet.test
 * Description:
 *
 * @Date:2020/8/30 10:44
 * @Author:$licunzhuang@13253633031
 */
public class HashZ {
    final V putVal(K key, V value, boolean onlyIfAbsent) {
//        如果key或者value为null直接抛出异常
        if (key == null || value == null) throw new NullPointerException();
//        hash进行了两次扰动
        int hash = spread(key.hashCode());
        int binCount = 0;
        for (ConcurrentHashMap.Node<K,V>[] tab = table;;) {
            ConcurrentHashMap.Node<K,V> f; int n, i, fh;
            if (tab == null || (n = tab.length) == 0)
//                初始化table数组，table数组就是用来存放元素的
                tab = initTable();
            else if ((f = tabAt(tab, i = (n - 1) & hash)) == null) {
//                在下标i处为空时，不用考虑链表还是树结构，直接通过cas操作在下标i处放置元素
                if (casTabAt(tab, i, null,
                        new ConcurrentHashMap.Node<K,V>(hash, key, value, null)))
                    break;                   // no lock when adding to empty bin
            }
            else if ((fh = f.hash) == MOVED)
//              在这种条件下，数组正在进行扩容，可以先帮助其扩容再添加元素
                tab = helpTransfer(tab, f);
            else {
//                正常的插入元素
                V oldVal = null;
//                取出下标为i的元素并作为加锁对象
                synchronized (f) {
                    if (tabAt(tab, i) == f) {
//                        在为链表结构的情况下
                        if (fh >= 0) {
                            binCount = 1;
                            for (ConcurrentHashMap.Node<K,V> e = f;; ++binCount) {
                                K ek;
//                                在hash冲突并且对象相等时直接取代旧值，并结束循环
                                if (e.hash == hash &&
                                        ((ek = e.key) == key ||
                                                (ek != null && key.equals(ek)))) {
                                    oldVal = e.val;
                                    if (!onlyIfAbsent)
                                        e.val = value;
                                    break;
                                }
//                                遍历到最后一个元素，都不相等则在尾部插入元素
                                ConcurrentHashMap.Node<K,V> pred = e;
                                if ((e = e.next) == null) {
                                    pred.next = new ConcurrentHashMap.Node<K,V>(hash, key,
                                            value, null);
                                    break;
                                }
                            }
                        }
//                        在为树结构的情况下
                        else if (f instanceof ConcurrentHashMap.TreeBin) {
                            ConcurrentHashMap.Node<K,V> p;
                            binCount = 2;
//                            插入红黑树结构的方法
                            if ((p = ((ConcurrentHashMap.TreeBin<K,V>)f).putTreeVal(hash, key,
                                    value)) != null) {
                                oldVal = p.val;
                                if (!onlyIfAbsent)
                                    p.val = value;
                            }
                        }
                    }
                }
                if (binCount != 0) {
//                    这里已经跳出锁范围，判断链表长度是否大于等于8，如果是则转变成树结构。
//                    里面会再次进行加锁操作。
                    if (binCount >= TREEIFY_THRESHOLD)
                        treeifyBin(tab, i);
                    if (oldVal != null)
                        return oldVal;
                    break;
                }
            }
        }
//        在这里将数组加1，并在里面进行是否扩容操作的判断。
        addCount(1L, binCount);
        return null;
    }
//    初始化table
    private final ConcurrentHashMap.Node<K,V>[] initTable() {
        ConcurrentHashMap.Node<K,V>[] tab; int sc;
        while ((tab = table) == null || tab.length == 0) {
//            sizectl小于0时代表有线程正在进行初始化或者扩容
            if ((sc = sizeCtl) < 0)
//                让出线程
                Thread.yield();
//            修改sizectl标识线程正在进行初始化
            else if (U.compareAndSwapInt(this, SIZECTL, sc, -1)) {
                try {
                    if ((tab = table) == null || tab.length == 0) {
//                        初始化容量为16（DEFAULT_CAPACITY）
                        int n = (sc > 0) ? sc : DEFAULT_CAPACITY;
                        @SuppressWarnings("unchecked")
                        ConcurrentHashMap.Node<K,V>[] nt = (ConcurrentHashMap.Node<K,V>[])new ConcurrentHashMap.Node<?,?>[n];
                        table = tab = nt;
                        sc = n - (n >>> 2);
                    }
                } finally {
                    sizeCtl = sc;
                }
                break;
            }
        }
        return tab;
    }

    private final void treeifyBin(ConcurrentHashMap.Node<K,V>[] tab, int index) {
        ConcurrentHashMap.Node<K,V> b; int n, sc;
        if (tab != null) {
//            在这里面还会进行判断数组长度是否小于64，如果是，则不是进行转换成树的操作而是对数组进行扩容。
            if ((n = tab.length) < MIN_TREEIFY_CAPACITY)
                tryPresize(n << 1);
            else if ((b = tabAt(tab, index)) != null && b.hash >= 0) {
//                在这里又进行了加锁操作，这是必要的。转换成树的同时，不允许进行其他操作。
                synchronized (b) {
                    if (tabAt(tab, index) == b) {
                        ConcurrentHashMap.TreeNode<K,V> hd = null, tl = null;
                        for (ConcurrentHashMap.Node<K,V> e = b; e != null; e = e.next) {
//                          先转换节点类型，其里面有left，right,pre，red等,以及继承自node节点的next
                            ConcurrentHashMap.TreeNode<K,V> p =
                                    new ConcurrentHashMap.TreeNode<K,V>(e.hash, e.key, e.val,
                                            null, null);
                            if ((p.prev = tl) == null)
                                hd = p;
                            else
                                tl.next = p;
                            tl = p;
                        }
//                       这里面转换成红黑树树结构并放在下标i处，在这里Treebin的hash为-2代表红黑树结构，其first属性为hd
                        setTabAt(tab, index, new ConcurrentHashMap.TreeBin<K,V>(hd));
                    }
                }
            }
        }
    }
    private final void transfer(ConcurrentHashMap.Node<K,V>[] tab, ConcurrentHashMap.Node<K,V>[] nextTab) {
        int n = tab.length, stride;
        if ((stride = (NCPU > 1) ? (n >>> 3) / NCPU : n) < MIN_TRANSFER_STRIDE)
//            每个线程应该处理的元素下标个数
            stride = MIN_TRANSFER_STRIDE; // subdivide range
//        初次转移元素时需要的初始化操作，初始化transferIndex(转移元素的最大下标)及新的数组等
        if (nextTab == null) {            // initiating
            try {
                @SuppressWarnings("unchecked")
                ConcurrentHashMap.Node<K,V>[] nt = (ConcurrentHashMap.Node<K,V>[])new ConcurrentHashMap.Node<?,?>[n << 1];
                nextTab = nt;
            } catch (Throwable ex) {      // try to cope with OOME
                sizeCtl = Integer.MAX_VALUE;
                return;
            }
            nextTable = nextTab;
            transferIndex = n;
        }
        int nextn = nextTab.length;
//        fwd表示目前正有线程在转移该下标处i的链表，在转移结束后会在旧的数组下标处放置该元素进行标识
        ConcurrentHashMap.ForwardingNode<K,V> fwd = new ConcurrentHashMap.ForwardingNode<K,V>(nextTab);
//        用来标识一个数组下标的转移是否结束
        boolean advance = true;
        boolean finishing = false; // to ensure sweep before committing nextTab
        for (int i = 0, bound = 0;;) {
            ConcurrentHashMap.Node<K,V> f; int fh;
            while (advance) {
                int nextIndex, nextBound;
//                其中一个下标转移结束后将advance设置为false继续下一个
                if (--i >= bound || finishing)
                    advance = false;
//                所有的元素都完成了下标重新分配将i置为-1
                else if ((nextIndex = transferIndex) <= 0) {
                    i = -1;
                    advance = false;
                }
                //本线程分配的下标范围完成，继续完成未分配的下标范围。nextIndex指明了分配与未分配下标的界限
                else if (U.compareAndSwapInt
                        (this, TRANSFERINDEX, nextIndex,
                                nextBound = (nextIndex > stride ?
                                        nextIndex - stride : 0))) {
                    bound = nextBound;
                    i = nextIndex - 1;
                    advance = false;
                }
            }
//            从这里可以看出上面为什么将i置为-1
            if (i < 0 || i >= n || i + n >= nextn) {
                int sc;
                if (finishing) {
                    nextTable = null;
                    table = nextTab;
//                    说明了sizeCtl里面包含有扩容线程的信息
                    sizeCtl = (n << 1) - (n >>> 1);
                    return;
                }
                if (U.compareAndSwapInt(this, SIZECTL, sc = sizeCtl, sc - 1)) {
                    if ((sc - 2) != resizeStamp(n) << RESIZE_STAMP_SHIFT)
                        return;
                    finishing = advance = true;
                    i = n; // recheck before commit
                }
            }
            else if ((f = tabAt(tab, i)) == null)
                advance = casTabAt(tab, i, null, fwd);
            else if ((fh = f.hash) == MOVED)
                advance = true; // already processed
            else {
//                这里是进行下标重新计算的代码主体，这里还是加了锁的。
                synchronized (f) {
                    if (tabAt(tab, i) == f) {
                        ConcurrentHashMap.Node<K,V> ln, hn;
                        if (fh >= 0) {
                            int runBit = fh & n;
                            ConcurrentHashMap.Node<K,V> lastRun = f;
                            for (ConcurrentHashMap.Node<K,V> p = f.next; p != null; p = p.next) {
                                int b = p.hash & n;
                                if (b != runBit) {
                                    runBit = b;
                                    lastRun = p;
                                }
                            }
                            if (runBit == 0) {
                                ln = lastRun;
                                hn = null;
                            }
                            else {
                                hn = lastRun;
                                ln = null;
                            }
//                            真正重新分配下标的代码，与旧table的长度进行与操作，为0放在原下标，1则放在原下标+n的地方
                            for (ConcurrentHashMap.Node<K,V> p = f; p != lastRun; p = p.next) {
                                int ph = p.hash; K pk = p.key; V pv = p.val;
                                if ((ph & n) == 0)
                                    ln = new ConcurrentHashMap.Node<K,V>(ph, pk, pv, ln);
                                else
                                    hn = new ConcurrentHashMap.Node<K,V>(ph, pk, pv, hn);
                            }
                            setTabAt(nextTab, i, ln);
                            setTabAt(nextTab, i + n, hn);
//                            这里放置了fwb表示正在扩容。
                            setTabAt(tab, i, fwd);
                            advance = true;
                        }
                        else if (f instanceof ConcurrentHashMap.TreeBin) {
                            ConcurrentHashMap.TreeBin<K,V> t = (ConcurrentHashMap.TreeBin<K,V>)f;
                            ConcurrentHashMap.TreeNode<K,V> lo = null, loTail = null;
                            ConcurrentHashMap.TreeNode<K,V> hi = null, hiTail = null;
                            int lc = 0, hc = 0;
                            for (ConcurrentHashMap.Node<K,V> e = t.first; e != null; e = e.next) {
                                int h = e.hash;
                                ConcurrentHashMap.TreeNode<K,V> p = new ConcurrentHashMap.TreeNode<K,V>
                                        (h, e.key, e.val, null, null);
                                if ((h & n) == 0) {
                                    if ((p.prev = loTail) == null)
                                        lo = p;
                                    else
                                        loTail.next = p;
                                    loTail = p;
                                    ++lc;
                                }
                                else {
                                    if ((p.prev = hiTail) == null)
                                        hi = p;
                                    else
                                        hiTail.next = p;
                                    hiTail = p;
                                    ++hc;
                                }
                            }
//                            少于等于6时转换成链表
                            ln = (lc <= UNTREEIFY_THRESHOLD) ? untreeify(lo) :
                                    (hc != 0) ? new ConcurrentHashMap.TreeBin<K,V>(lo) : t;
                            hn = (hc <= UNTREEIFY_THRESHOLD) ? untreeify(hi) :
                                    (lc != 0) ? new ConcurrentHashMap.TreeBin<K,V>(hi) : t;
                            setTabAt(nextTab, i, ln);
                            setTabAt(nextTab, i + n, hn);
                            setTabAt(tab, i, fwd);
                            advance = true;
                        }
                    }
                }
            }
        }
    }
    final ConcurrentHashMap.Node<K,V>[] helpTransfer(ConcurrentHashMap.Node<K,V>[] tab, ConcurrentHashMap.Node<K,V> f) {
        ConcurrentHashMap.Node<K,V>[] nextTab; int sc;
        if (tab != null && (f instanceof ConcurrentHashMap.ForwardingNode) &&
                (nextTab = ((ConcurrentHashMap.ForwardingNode<K,V>)f).nextTable) != null) {
            int rs = resizeStamp(tab.length);
//            扩容并转移元素是否结束
            while (nextTab == nextTable && table == tab &&
                    (sc = sizeCtl) < 0) {
                if ((sc >>> RESIZE_STAMP_SHIFT) != rs || sc == rs + 1 ||
                        sc == rs + MAX_RESIZERS || transferIndex <= 0)
                    break;
                if (U.compareAndSwapInt(this, SIZECTL, sc, sc + 1)) {
                    transfer(tab, nextTab);
                    break;
                }
            }
//            返回扩容和转移后的新table
            return nextTab;
        }
        return table;
    }

}
