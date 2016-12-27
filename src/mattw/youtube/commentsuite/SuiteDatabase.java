package mattw.youtube.commentsuite;

import java.awt.Graphics;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.imageio.ImageIO;
import javax.swing.ImageIcon;

public class SuiteDatabase {
	
	public String dbfile;
	public SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd hh:mm a");
	
	public Connection con;
	public Statement s;
	
	public SuiteDatabase(String db) {
		dbfile = db;
	}
	
	public void clean() throws SQLException {
		if(!con.getAutoCommit())
			con.commit();
		con.setAutoCommit(true);
		con.setAutoCommit(true);
		s.execute("VACUUM");
	}
	
	public void dropAllTables() throws SQLException {
		if(!con.getAutoCommit())
			con.commit();
		System.out.println(con.getAutoCommit());
		con.setAutoCommit(true);
		System.out.println(con.getAutoCommit());
		con.setAutoCommit(true);
		System.out.println(con.getAutoCommit());
		for(String table : "gitem_type,gitem_list,groups,group_gitem,video_group,videos,comments,channels".split(","))
			s.executeUpdate("DROP TABLE IF EXISTS "+table);
	}
	
	public void setup() throws ClassNotFoundException, SQLException {
		Class.forName("org.sqlite.JDBC");
		con = DriverManager.getConnection("jdbc:sqlite:"+dbfile);
		s = con.createStatement();
		
		s.executeUpdate("CREATE TABLE IF NOT EXISTS gitem_type (type_id INTEGER PRIMARY KEY, name STRING);");
		s.executeUpdate("INSERT OR IGNORE INTO gitem_type VALUES (0, 'video'),(1, 'channel'),(2, 'playlist');");
		
		s.executeUpdate("CREATE TABLE IF NOT EXISTS gitem_list ("
				+ "gitem_id INTEGER PRIMARY KEY,"
				+ "type_id INTEGER,"
				+ "youtube_id STRING UNIQUE," // Could be video_id, channel_id, or playlist_id
				+ "title STRING,"
				+ "channel_title STRING,"
				+ "published DATE,"
				+ "last_checked DATE,"
				+ "thumb_url STRING, "
				+ "FOREIGN KEY(type_id) REFERENCES gitem_type(type_id));");
		
		s.executeUpdate("CREATE TABLE IF NOT EXISTS groups (group_id INTEGER PRIMARY KEY AUTOINCREMENT, group_name STRING UNIQUE);");
		
		s.executeUpdate("INSERT OR IGNORE INTO groups VALUES (0, 'Default');");
		
		s.executeUpdate("CREATE TABLE IF NOT EXISTS group_gitem ("
				+ "group_id INTEGER,"
				+ "gitem_id INTEGER,"
				+ "FOREIGN KEY(group_id) REFERENCES groups(group_id),"
				+ "FOREIGN KEY(gitem_id) REFERENCES gitem_list(gitem_id));");
		
		s.executeUpdate("CREATE TABLE IF NOT EXISTS video_group ("
				+ "gitem_id INTEGER,"
				+ "video_id STRING,"
				+ "FOREIGN KEY(gitem_id) REFERENCES gitem_list(gitem_id),"
				+ "FOREIGN KEY(video_id) REFERENCES videos(video_id));");
		
		s.executeUpdate("CREATE TABLE IF NOT EXISTS videos ("
				+ "video_id STRING PRIMARY KEY,"
				+ "channel_id STRING,"
				+ "grab_date INTEGER,"
				+ "publish_date INTEGER,"
				+ "video_title STRING,"
				+ "total_comments INTEGER,"
				+ "total_views INTEGER,"
				+ "total_likes INTGEGER,"
				+ "total_dislikes INTEGER,"
				+ "video_desc STRING,"
				+ "thumb_url STRING,"
				+ "FOREIGN KEY(channel_id) REFERENCES channels(channel_id))");
		
		s.executeUpdate("CREATE TABLE IF NOT EXISTS comments ("
				+ "comment_id STRING PRIMARY KEY,"
				+ "channel_id STRING,"
				+ "video_id STRING,"
				+ "comment_date INTEGER,"
				+ "comment_likes INTEGER,"
				+ "reply_count INTEGER,"
				+ "is_reply BOOLEAN,"
				+ "parent_id STRING,"
				+ "comment_text TEXT,"
				+ "FOREIGN KEY(channel_id) REFERENCES channels(channel_id),"
				+ "FOREIGN KEY(video_id) REFERENCES videos(video_id))");
		
		// Removed channel_url as it saves space and can be done by just "https://youtube.com/channel/"+channel_id
		s.executeUpdate("CREATE TABLE IF NOT EXISTS channels ("
				+ "channel_id STRING PRIMARY KEY,"
				+ "channel_name STRING,"
				+ "channel_profile_url STRING,"
				+ "channel_profile BLOB)");
	}
	
	public void insertComments(List<Comment> comments) throws SQLException {
		System.out.println("Inserting "+comments.size()+" comments");
		PreparedStatement ps = con.prepareStatement("INSERT OR IGNORE INTO comments (comment_id, channel_id, video_id, comment_date, comment_text, comment_likes, reply_count, is_reply, parent_id) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)");
		for(Comment c : comments) {
			if(c != null && ps != null) {
				ps.setString(1, c.comment_id);
				ps.setString(2, c.channel.channel_id);
				ps.setString(3, c.video_id);
				ps.setLong(4, c.comment_date.getTime());
				ps.setString(5, c.comment_text);
				ps.setLong(6, c.comment_likes);
				ps.setLong(7, c.reply_count);
				ps.setBoolean(8, c.is_reply);
				ps.setString(9, c.parent_id);
				ps.addBatch();
			} else {
				System.out.println("NULL VALUE ON COMMENT INSERT c:"+(c==null)+",ps:"+(ps==null)+"");
			}
		}
		ps.executeBatch();
		System.out.println("Inserted "+comments.size()+" comments");
	}
	
	public List<String> getCommentIDs(String group_name) throws SQLException {
		PreparedStatement ps = con.prepareStatement("SELECT comment_id FROM comments "
				+ "LEFT JOIN videos ON videos.video_id = comments.video_id "
				+ "WHERE videos.video_id IN ( "
				+ "    SELECT video_id FROM video_group "
				+ "    LEFT JOIN group_gitem ON video_group.gitem_id = group_gitem.gitem_id "
				+ "    LEFT JOIN groups ON groups.group_id = group_gitem.group_id "
				+ "    WHERE group_name = ?)");
		ps.setString(1, group_name);
		ResultSet rs = ps.executeQuery();
		
		List<String> list = new ArrayList<String>();
		while(rs.next()) {
			list.add(rs.getString("comment_id"));
		}
		return list;
	}
	
	public List<String> getAllChannelIDs() throws SQLException {
		ResultSet rs = s.executeQuery("SELECT DISTINCT channel_id FROM channels");
		
		List<String> list = new ArrayList<String>();
		while(rs.next()) {
			list.add(rs.getString("channel_id"));
		}
		return list;
	}
	
	public List<Comment> getCommentTree(String commentId) throws SQLException {
		PreparedStatement ps = con.prepareStatement("SELECT * FROM comments "
				+ "LEFT JOIN channels ON channels.channel_id = comments.channel_id "
				+ "WHERE comment_id = ? OR parent_id = ? "
				+ "ORDER BY is_reply ASC, comment_date ASC");
		ps.setString(1, commentId);
		ps.setString(2, commentId);
		ResultSet rs = ps.executeQuery();
		
		List<Comment> list = new ArrayList<Comment>();
		Map<String, Channel> channels = new HashMap<String, Channel>();
		while(rs.next()) {
			Channel author;
			if(channels.containsKey(rs.getString("channel_id"))) {
				author = channels.get(rs.getString("channel_id"));
			} else {
				ImageIcon channel_profile = bytesToImageIcon(rs.getBytes("channel_profile"));
				if(channel_profile != null) channel_profile = new ImageIcon(channel_profile.getImage().getScaledInstance(24, 24, 0));
				author = new Channel(rs.getString("channel_id"), rs.getString("channel_name"), rs.getString("channel_profile_url"), channel_profile);
			}
			Comment comment = new Comment(rs.getString("comment_id"), author, rs.getString("video_id"), new Date(rs.getLong("comment_date")), rs.getString("comment_text"), rs.getLong("comment_likes"), rs.getLong("reply_count"), rs.getBoolean("is_reply"), rs.getString("parent_id"));
			list.add(comment);
		}
		return list;
	}
	
	public List<Comment> getComments(String group_name, int orderby, String name_like, String text_like, int limit, GroupItem gitem, int type) throws SQLException {
		String order = "comment_date DESC ";
		if(orderby == 1) order = "comment_date ASC ";
		if(orderby == 2) order = "comment_likes DESC ";
		if(orderby == 3) order = "reply_count DESC ";
		if(orderby == 4) order = "channel_name ASC, comment_date DESC ";
		if(orderby == 5) order = "comment_text ASC ";
		
		String ctype = "";
		if(type == 1) ctype = " AND is_reply = 0 ";
		if(type == 2) ctype = " AND is_reply = 1 ";
		
		PreparedStatement ps;
		if(gitem == null) {
			ps = con.prepareStatement("SELECT * FROM comments "
					+ "LEFT JOIN channels ON channels.channel_id = comments.channel_id "
					+ "LEFT JOIN videos ON videos.video_id = comments.video_id "
					+ "WHERE videos.video_id IN ( "
					+ "    SELECT video_id FROM video_group "
					+ "    LEFT JOIN group_gitem ON video_group.gitem_id = group_gitem.gitem_id "
					+ "    LEFT JOIN groups ON groups.group_id = group_gitem.group_id "
					+ "    WHERE group_name = ?)"
					+ "AND channel_name LIKE ? AND comment_text LIKE ? "+ctype
					+ "ORDER BY "+order
					+ "LIMIT ?");
			ps.setString(1, group_name);
			ps.setString(2, "%"+name_like+"%");
			ps.setString(3, "%"+text_like+"%");
			ps.setInt(4, limit);
		} else {
			ps = con.prepareStatement("SELECT * FROM comments "
					+ "LEFT JOIN channels ON channels.channel_id = comments.channel_id "
					+ "LEFT JOIN videos ON videos.video_id = comments.video_id "
					+ "LEFT JOIN video_group ON video_group.video_id = videos.video_id "
					+ "WHERE video_group.gitem_id = ? "
					+ "AND channel_name LIKE ? AND comment_text LIKE ? "+ctype
					+ "ORDER BY "+order
					+ "LIMIT ?");
			ps.setInt(1, gitem.gitem_id);
			ps.setString(2, "%"+name_like+"%");
			ps.setString(3, "%"+text_like+"%");
			ps.setInt(4, limit);
		}
		ResultSet rs = ps.executeQuery();
		
		List<Comment> list = new ArrayList<Comment>();
		Map<String, Channel> channels = new HashMap<String, Channel>();
		while(rs.next()) {
			Channel author;
			if(channels.containsKey(rs.getString("channel_id"))) {
				author = channels.get(rs.getString("channel_id"));
			} else {
				ImageIcon channel_profile = bytesToImageIcon(rs.getBytes("channel_profile"));
				if(channel_profile != null) channel_profile = new ImageIcon(channel_profile.getImage().getScaledInstance(24, 24, 0));
				author = new Channel(rs.getString("channel_id"), rs.getString("channel_name"), rs.getString("channel_profile_url"), channel_profile);
			}
			Comment comment = new Comment(rs.getString("comment_id"), author, rs.getString("video_id"), new Date(rs.getLong("comment_date")), rs.getString("comment_text"), rs.getLong("comment_likes"), rs.getLong("reply_count"), rs.getBoolean("is_reply"), rs.getString("parent_id"));
			list.add(comment);
		}
		return list;
	}
	
	public List<String> getAllVideoIds() throws SQLException {
		ResultSet rs = s.executeQuery("SELECT video_id FROM videos");
		List<String> list = new ArrayList<String>();
		while(rs.next()) {
			list.add(rs.getString("video_id"));
		}
		return list;
	}
	
	public List<String> getVideoIds(String group_name) throws SQLException {
		PreparedStatement ps = con.prepareStatement("SELECT * FROM videos "
				+ "LEFT JOIN channels ON channels.channel_id = videos.channel_id "
				+ "WHERE videos.video_id IN ( "
				+ "    SELECT video_id FROM video_group "
				+ "    LEFT JOIN group_gitem ON video_group.gitem_id = group_gitem.gitem_id "
				+ "    LEFT JOIN groups ON groups.group_id = group_gitem.group_id "
				+ "    WHERE group_name = ?) ");
		ps.setString(1, group_name);
		ResultSet rs = ps.executeQuery();
		
		List<String> list = new ArrayList<String>();
		while(rs.next()) {
			list.add(rs.getString("video_id"));
		}
		return list;
	}
	
	public Video getVideo(String videoId) throws SQLException, ParseException {
		PreparedStatement ps = con.prepareStatement("SELECT * FROM videos "
				+ "LEFT JOIN channels ON channels.channel_id = videos.channel_id "
				+ "WHERE videos.video_id = ?");
		ps.setString(1, videoId);
		ResultSet rs = ps.executeQuery();
		if(rs.next()) {
			ImageIcon channel_profile = bytesToImageIcon(rs.getBytes("channel_profile"));
			Channel author = new Channel(rs.getString("channel_id"), rs.getString("channel_name"), rs.getString("channel_profile_url"), channel_profile);
			Video video = new Video(rs.getString("video_id"), author, new Date(rs.getLong("grab_date")), new Date(rs.getLong("publish_date")), rs.getString("video_title"), rs.getString("video_desc"), rs.getLong("total_comments"), rs.getLong("total_likes"), rs.getLong("total_dislikes"), rs.getLong("total_views"), rs.getString("thumb_url"));
			return video;
		}
		return null;
	}
	
	public List<Video> getVideos(String group_name) throws SQLException, ParseException {
		PreparedStatement ps = con.prepareStatement("SELECT * FROM videos "
				+ "LEFT JOIN channels ON channels.channel_id = videos.channel_id "
				+ "WHERE videos.video_id IN ( "
				+ "    SELECT video_id FROM video_group "
				+ "    LEFT JOIN group_gitem ON video_group.gitem_id = group_gitem.gitem_id "
				+ "    LEFT JOIN groups ON groups.group_id = group_gitem.group_id "
				+ "    WHERE group_name = ?) "
				+ "ORDER BY videos.publish_date DESC");
		ps.setString(1, group_name);
		ResultSet rs = ps.executeQuery();
		
		List<Video> list = new ArrayList<Video>();
		Map<String, Channel> channels = new HashMap<String, Channel>();
		while(rs.next()) {
			Channel author;
			if(channels.containsKey(rs.getString("channel_id"))) {
				author = channels.get(rs.getString("channel_id"));
			} else {
				ImageIcon channel_profile = bytesToImageIcon(rs.getBytes("channel_profile"));
				author = new Channel(rs.getString("channel_id"), rs.getString("channel_name"), rs.getString("channel_profile_url"), channel_profile);
				channels.put(rs.getString("channel_id"), author);
			}
			Video video = new Video(rs.getString("video_id"), author, new Date(rs.getLong("grab_date")), new Date(rs.getLong("publish_date")), rs.getString("video_title"), rs.getString("video_desc"), rs.getLong("total_comments"), rs.getLong("total_likes"), rs.getLong("total_dislikes"), rs.getLong("total_views"), rs.getString("thumb_url"));
			list.add(video);
		}
		return list;
	}
	
	public void insertVideos(List<Video> videos) throws SQLException {
		System.out.println("Inserting "+videos.size()+" videos");
		PreparedStatement ps = con.prepareStatement("INSERT OR IGNORE INTO videos (video_id, channel_id, grab_date, publish_date, video_title, total_comments, total_views, total_likes, total_dislikes, video_desc, thumb_url) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)");
		for(Video v : videos) {
			ps.setString(1, v.video_id);
			ps.setString(2, v.channel.channel_id);
			ps.setLong(3, v.grab_date.getTime());
			ps.setLong(4, v.publish_date.getTime());
			ps.setString(5, v.video_title);
			ps.setLong(6, v.total_comments);
			ps.setLong(7, v.total_views);
			ps.setLong(8, v.total_likes);
			ps.setLong(9, v.total_dislikes);
			ps.setString(10, v.video_desc);
			ps.setString(11, v.thumb_url);
			ps.addBatch();
		}
		ps.executeBatch();
		System.out.println("Inserted "+videos.size()+" videos");
	}
	
	public void updateVideos(List<Video> videos) throws SQLException {
		System.out.println("Updating "+videos.size()+" videos");
		PreparedStatement ps = con.prepareStatement("UPDATE videos SET grab_date = ?, "
				+ "video_title = ?, "
				+ "total_comments = ?, "
				+ "total_views = ?, "
				+ "total_likes = ?, "
				+ "total_dislikes = ?, "
				+ "video_desc = ?, "
				+ "thumb_url = ? "
				+ "WHERE video_id = ?");
		for(Video v : videos) {
			ps.setLong(1, System.currentTimeMillis());
			ps.setString(2, v.video_title);
			ps.setLong(3, v.total_comments);
			ps.setLong(4, v.total_views);
			ps.setLong(5, v.total_likes);
			ps.setLong(6, v.total_dislikes);
			ps.setString(7, v.video_desc);
			ps.setString(8, v.thumb_url);
			ps.setString(9, v.video_id);
			ps.addBatch();
		}
		ps.executeBatch();
		System.out.println("Updated "+videos.size()+" videos");
	}
	
	public void insertChannels(List<Channel> channels) throws SQLException {
		System.out.println("Inserting "+channels.size()+" channels");
		PreparedStatement ps = con.prepareStatement("INSERT OR IGNORE INTO channels (channel_id, channel_name, channel_profile_url, channel_profile) VALUES (?, ?, ?, ?)");
		for(Channel c : channels) {
			if(c != null && ps != null) {
				ps.setString(1, c.channel_id);
				ps.setString(2, c.channel_name);
				ps.setString(3, c.channel_profile_url);
				ps.setBytes(4, imageIconToBytes(c.channel_profile));
			} else {
				System.out.println("NULL VALUE ON CHANNEL INSERT c:"+(c==null)+",ps:"+(ps==null)+"");
			}
			
			ps.addBatch();
		}
		ps.executeBatch();
		System.out.println("Inserted "+channels.size()+" channels");
	}
	
	public void updateChannels(List<Channel> channels) throws SQLException {
		PreparedStatement ps = con.prepareStatement("UPDATE channels SET "
				+ "channel_name = ?, "
				+ "channel_profile_url = ?, "
				+ "channel_profile = ? "
				+ "WHERE channel_id = ?");
		for(Channel c : channels) {
			ps.setString(1, c.channel_name);
			ps.setString(2, c.channel_profile_url);
			ps.setBytes(3, imageIconToBytes(c.channel_profile));
			ps.addBatch();
		}
		System.out.println("Updating "+channels.size()+" channels");
		ps.executeBatch();
		System.out.println("Updated "+channels.size()+" channels");
	}
	
	public void insertVideoGroups(List<VideoGroup> video_groups) throws SQLException {
		PreparedStatement ps = con.prepareStatement("INSERT INTO video_group (gitem_id, video_id) VALUES (?, ?)");
		for(VideoGroup vg : video_groups) {
			ps.setInt(1, vg.gitem_id);
			ps.setString(2, vg.video_id);
			ps.addBatch();
		}
		System.out.println("Inserting "+video_groups.size()+" video groups");
		ps.executeBatch();
		System.out.println("Inserted "+video_groups.size()+" video groups");
	}
	
	public void updateGroupItemsChecked(List<GroupItem> items, Date date) throws SQLException {
		PreparedStatement ps = con.prepareStatement("UPDATE gitem_list SET last_checked = ? WHERE gitem_id = ?");
		for(GroupItem gi : items) {
			ps.setLong(1, date.getTime());
			ps.setInt(2, gi.gitem_id);
			ps.addBatch();
		}
		ps.executeBatch();
	}
	
	public void insertGroupItems(String group_name, List<GroupItem> items) throws SQLException {
		Group g = getGroup(group_name);
		if(g == null) g = getGroup(0);
		int gitem_id = s.executeQuery("SELECT MAX(gitem_id) AS max_id FROM gitem_list").getInt("max_id") + 1;
		System.out.println(gitem_id);
		PreparedStatement ps = con.prepareStatement("INSERT OR IGNORE INTO gitem_list (gitem_id, type_id, youtube_id, title, channel_title, published, last_checked, thumb_url) VALUES (?, ?, ?, ?, ?, ?, ?, ?)");
		PreparedStatement ps1 = con.prepareStatement("INSERT INTO group_gitem (group_id, gitem_id) VALUES (?, ?)");
		System.out.println("Inserting "+items.size()+" Group Items");
		for(GroupItem gi : items) {
			ps.setInt(1, gitem_id);
			ps.setInt(2, gi.type_id);
			ps.setString(3, gi.youtube_id);
			ps.setString(4, gi.title);
			ps.setString(5, gi.channel_title);
			ps.setLong(6, gi.published.getTime());
			// ps.setBytes(7, imageIconToBytes(gi.thumbnail));
			ps.setLong(7, gi.last_checked.getTime());
			ps.setString(8, gi.thumb_url);
			ps.addBatch();
			
			ps1.setInt(1, g.group_id);
			ps1.setInt(2, gitem_id);
			ps1.addBatch();
			gitem_id++;
		}
		ps.executeBatch();
		ps1.executeBatch();
		System.out.println("Inserted "+items.size()+" Group Items");
	}
	
	public List<GroupItem> getGroupItems(String group_name) throws SQLException {
		PreparedStatement ps = con.prepareStatement("SELECT * FROM gitem_list "
				+ "LEFT JOIN gitem_type ON gitem_type.type_id = gitem_list.type_id "
				+ "LEFT JOIN group_gitem ON group_gitem.gitem_id = gitem_list.gitem_id "
				+ "LEFT JOIN groups ON groups.group_id = group_gitem.group_id "
				+ "WHERE group_name = ?");
		ps.setString(1, group_name);
		ResultSet rs = ps.executeQuery();
		
		List<GroupItem> list = new ArrayList<GroupItem>();
		while(rs.next()) {
			// ImageIcon thumb = bytesToImageIcon(rs.getBytes("thumbnail"));
			GroupItem gi = new GroupItem(rs.getInt("type_id"), rs.getString("name"), rs.getString("youtube_id"), rs.getString("title"), rs.getString("channel_title"), new Date(rs.getLong("published")), new Date(rs.getLong("last_checked")), rs.getString("thumb_url"));
			gi.setID(rs.getInt("gitem_id"));
			list.add(gi);
		}
		return list;
	}
	
	public void removeGroup(Group g) throws SQLException {
		// PreparedStatement ps = con.prepareStatement("DELETE FROM groups, group_items, video_group");
		// TODO
	}
	
	public Group getGroup(int group_id) throws SQLException {
		PreparedStatement q = con.prepareStatement("SELECT * FROM groups WHERE group_id = ?");
		q.setInt(1, group_id);
		ResultSet rs = q.executeQuery();
		if(rs.next()) {
			return new Group(rs.getInt("group_id"), rs.getString("group_name"));
		} else {
			return null;
		}
	}
	
	public Group getGroup(String group_name) throws SQLException {
		PreparedStatement q = con.prepareStatement("SELECT * FROM groups WHERE group_name = ?");
		q.setString(1, group_name);
		ResultSet rs = q.executeQuery();
		if(rs.next()) {
			return new Group(rs.getInt("group_id"), rs.getString("group_name"));
		} else {
			return null;
		}
	}
	
	public List<Group> getGroups() throws SQLException {
		List<Group> groups = new ArrayList<Group>();
		ResultSet rs = s.executeQuery("SELECT * FROM groups");
		while(rs.next()) {
			groups.add(new Group(rs.getInt("group_id"), rs.getString("group_name")));
		}
		return groups;
	}
	
	public List<VideoGroup> getVideoGroups() throws SQLException {
		List<VideoGroup> list = new ArrayList<VideoGroup>();
		ResultSet rs = s.executeQuery("SELECT * FROM video_group");
		while(rs.next()) {
			list.add(new VideoGroup(rs.getInt("gitem_id"), rs.getString("video_id")));
		}
		return list;
	}
	
	public void editGroupName(String group_name, String new_name) throws SQLException {
		PreparedStatement ps = con.prepareStatement("UPDATE groups SET group_name = ? WHERE group_name = ?");
		ps.setString(1, new_name);
		ps.setString(2, group_name);
		ps.executeUpdate();
	}
	
	public void createGroup(String name) throws SQLException {
		PreparedStatement ps = con.prepareStatement("INSERT INTO groups (group_name) VALUES (?)");
		ps.setString(1, name);
		ps.executeUpdate();
		System.out.println("Created new group ["+name+"]");
	}
	
	
	private ImageIcon bytesToImageIcon(byte[] bytes) {
		if(bytes == null) {
			return null;
		} else {
			return new ImageIcon(bytes);
		}
	}
	
	private byte[] imageIconToBytes(ImageIcon img) {
		if(img == null) return null;
		BufferedImage bi = new BufferedImage(img.getIconWidth(), img.getIconHeight(), BufferedImage.TYPE_INT_RGB);
		Graphics g = bi.createGraphics();
		img.paintIcon(null, g, 0, 0);
		g.dispose();
		
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		try {
			ImageIO.write(bi, "png", baos);
		} catch (IOException e) {
			return null;
		}
		return baos.toByteArray();
	}
}