package net.gate88.wars.arena;

import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.IncompleteRegionException;
import com.sk89q.worldedit.LocalSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.extent.clipboard.BlockArrayClipboard;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.extent.clipboard.io.BuiltInClipboardFormat;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormat;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormats;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardReader;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardWriter;
import com.sk89q.worldedit.function.operation.ForwardExtentCopy;
import com.sk89q.worldedit.function.operation.Operations;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.Region;
import com.sk89q.worldedit.session.ClipboardHolder;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import org.bukkit.Location;
import org.bukkit.entity.Player;

/**
 * WorldEdit API を直接触る部分。WorldEdit が無い環境でも読み込まれないよう、
 * MapStore から WorldEdit の有無を確認した後にだけ呼ぶ。
 */
final class WorldEditBridge {
    private WorldEditBridge() {}

    /** プレイヤーの範囲選択を schematic に保存。origin = 貼り付け時の基準点(=アリーナ中心)。戻り値 = ブロック数 */
    static long saveSelection(Player p, Location origin, File out) throws Exception {
        com.sk89q.worldedit.entity.Player wp = BukkitAdapter.adapt(p);
        LocalSession session = WorldEdit.getInstance().getSessionManager().get(wp);
        Region region;
        try {
            region = session.getSelection(wp.getWorld());
        } catch (IncompleteRegionException ex) {
            throw new IllegalStateException("先に WorldEdit で範囲を選択してください (//wand で左右クリック, または //pos1 //pos2)");
        }
        BlockArrayClipboard clip = new BlockArrayClipboard(region);
        clip.setOrigin(BlockVector3.at(origin.getBlockX(), origin.getBlockY(), origin.getBlockZ()));
        try (EditSession es = WorldEdit.getInstance().newEditSession(wp.getWorld())) {
            ForwardExtentCopy copy = new ForwardExtentCopy(es, region, clip, region.getMinimumPoint());
            copy.setCopyingEntities(false);
            Operations.complete(copy);
        }
        try (ClipboardWriter w = BuiltInClipboardFormat.SPONGE_V3_SCHEMATIC.getWriter(new FileOutputStream(out))) {
            w.write(clip);
        }
        return region.getVolume();
    }

    /** schematic を at に貼り付け (保存時の基準点が at に来る)。p の //undo で戻せる */
    static void paste(Player p, File in, Location at) throws Exception {
        ClipboardFormat fmt = ClipboardFormats.findByFile(in);
        if (fmt == null) throw new IllegalStateException("schematic の形式を判別できません: " + in.getName());
        Clipboard clip;
        try (ClipboardReader r = fmt.getReader(new FileInputStream(in))) {
            clip = r.read();
        }
        com.sk89q.worldedit.entity.Player wp = BukkitAdapter.adapt(p);
        LocalSession session = WorldEdit.getInstance().getSessionManager().get(wp);
        try (EditSession es = WorldEdit.getInstance().newEditSessionBuilder()
                .world(BukkitAdapter.adapt(at.getWorld())).actor(wp).maxBlocks(-1).build()) {
            Operations.complete(new ClipboardHolder(clip).createPaste(es)
                    .to(BlockVector3.at(at.getBlockX(), at.getBlockY(), at.getBlockZ()))
                    .ignoreAirBlocks(false)
                    .build());
            session.remember(es);
        }
    }
}
