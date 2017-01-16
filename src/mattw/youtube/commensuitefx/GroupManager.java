package mattw.youtube.commensuitefx;

import java.io.IOException;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.google.gson.JsonSyntaxException;

import javafx.application.Platform;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.VPos;
import javafx.scene.chart.AreaChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Button;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import mattw.youtube.datav3.YoutubeData;
import mattw.youtube.datav3.list.ChannelsList;
import mattw.youtube.datav3.list.CommentThreadsList;
import mattw.youtube.datav3.list.CommentsList;
import mattw.youtube.datav3.list.PlaylistItemsList;
import mattw.youtube.datav3.list.VideosList;

public class GroupManager extends StackPane {
	
	public static Map<Integer, GroupManager> managers = new HashMap<Integer, GroupManager>();
	public GroupManager manager;
	
	public ObservableList<GitemType> gi_list = FXCollections.observableArrayList();
	public ObservableList<VideoType> v_list = FXCollections.observableArrayList();
	
	public TabPane tabs = new TabPane();
	public Tab items, analytics;
	public Label gi_label, v_label;
	
	public ExecutorService es;
	public boolean refreshing = false;
	public Button close;
	
	public Group group;
	public int group_id;
	public static DatabaseManager database;
	public static YoutubeData data;
	
	public GroupManager(Group g, DatabaseManager database, YoutubeData data) {
		super();
		manager = this;
		GroupManager.database = database;
		GroupManager.data = data;
		
		setMaxHeight(Double.MAX_VALUE);
		setMaxWidth(Double.MAX_VALUE);
		group = g;
		group_id = g.group_id;
		
		VBox vbox = new VBox();
		
		getChildren().add(vbox);
		tabs.setMaxHeight(Double.MAX_VALUE);
		tabs.setMaxWidth(Double.MAX_VALUE);
		vbox.getChildren().add(tabs);
		VBox.setVgrow(tabs, Priority.ALWAYS);
		
		items = new Tab("Items and Videos");
		items.setClosable(false);
		
		GridPane grid = new GridPane();
		grid.setMaxHeight(Double.MAX_VALUE);
		grid.setAlignment(Pos.CENTER);
		grid.setVgap(5);
		grid.setHgap(5);
		grid.setPadding(new Insets(10,10,10,10));
		ColumnConstraints col1 = new ColumnConstraints();
		col1.setPercentWidth(40);
		ColumnConstraints col2 = new ColumnConstraints();
		col2.setPercentWidth(60);
		grid.getColumnConstraints().addAll(col1, col2);
		items.setContent(grid);
		
		gi_label = new Label("Add some items to this group.");
		gi_label.setMaxWidth(Double.MAX_VALUE);
		gi_label.setAlignment(Pos.CENTER);
		grid.add(gi_label, 0, 0);
		GridPane.setHgrow(gi_label, Priority.ALWAYS);
		
		TableView<GitemType> gi_table = new TableView<>();
		gi_table.setMaxHeight(Double.MAX_VALUE);
		gi_table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
		gi_table.setItems(gi_list);
		TableColumn<GitemType, String> typeCol = new TableColumn<>("Type");
		typeCol.setCellValueFactory(new PropertyValueFactory<>("typeText"));
		typeCol.setCellFactory(col -> new TableCell<GitemType, String>(){
			public void updateItem(String item, boolean empty) {
				if(empty || item == null) {
					setText(null);
				} else {
					setText(item);
				}
				setAlignment(Pos.CENTER);
			}
		});
		TableColumn<GitemType, String> gi_titleCol = new TableColumn<>("Title");
		gi_titleCol.setCellValueFactory(new PropertyValueFactory<>("title"));
		TableColumn<GitemType, Long> gi_checkedCol = new TableColumn<>("Last checked");
		gi_checkedCol.setCellValueFactory(new PropertyValueFactory<>("lastChecked"));
		gi_checkedCol.setCellFactory(col -> new TableCell<GitemType, Long>(){
			private SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy hh:mm a");
			public void updateItem(Long item, boolean empty) {
				if(empty || item == null) {
					setText(null);
				} else {
					setText(item > 0 ? sdf.format(new Date(item)) : "Never");
				}
				setAlignment(Pos.CENTER);
			}
		});
		gi_table.getColumns().add(typeCol);
		gi_table.getColumns().add(gi_titleCol);
		gi_table.getColumns().add(gi_checkedCol);
		grid.add(gi_table, 0, 1);
		GridPane.setVgrow(gi_table, Priority.ALWAYS);
		
		ContextMenu gi_menu = new ContextMenu();
		MenuItem gi_open = new MenuItem("Open in Browser");
		gi_open.setOnAction(e -> {
			GitemType gi = gi_table.getSelectionModel().getSelectedItem();
			CommentSuiteFX.openInBrowser(gi.getYoutubeLink());
		});
		gi_menu.getItems().add(gi_open);
		gi_table.setOnMouseClicked(e -> {
			if(e.isPopupTrigger()) {
				gi_menu.show(this, e.getScreenX(), e.getScreenY());
			}
		});
		
		
		v_label = new Label("Refresh this group to get video data.");
		v_label.setMaxWidth(Double.MAX_VALUE);
		v_label.setAlignment(Pos.CENTER);
		grid.add(v_label, 1, 0);
		GridPane.setHgrow(v_label, Priority.ALWAYS);
		
		TableView<VideoType> v_table = new TableView<>();
		v_table.setMaxHeight(Double.MAX_VALUE);
		v_table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
		v_table.setItems(v_list);
		TableColumn<VideoType,String> v_titleCol = new TableColumn<>("Title");
		v_titleCol.setCellValueFactory(new PropertyValueFactory<>("title"));
		v_titleCol.setCellFactory(col -> new TableCell<VideoType, String>(){
			public void updateItem(String item, boolean empty) {
				if(empty || item == null) {
					setText(null);
				} else {
					setText(item);
				}
				setAlignment(Pos.CENTER_LEFT);
			}
		});
		TableColumn<VideoType,VideoType> v_aboutCol = new TableColumn<>("About");
		v_aboutCol.setCellValueFactory(celldata -> new ReadOnlyObjectWrapper<VideoType>(celldata.getValue()));
		v_aboutCol.setCellFactory(col -> new TableCell<VideoType,VideoType>(){
			private VBox vbox;
			private Label author;
			private Label published;
			private Label comments;
			private SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy hh:mm a");
			{
				vbox = new VBox();
				author = createLabel();
				published = createLabel();
				published.setStyle("-fx-color: lightgray");
				published.setTextFill(Color.GOLDENROD);
				comments = createLabel();
				vbox.getChildren().addAll(author,published,comments);
				setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
			}
			private Label createLabel() {
				Label label = new Label();
				label.setMaxWidth(Double.MAX_VALUE);
				label.setAlignment(Pos.CENTER);
				VBox.setVgrow(label, Priority.ALWAYS);
				return label;
			}
			public void updateItem(VideoType video, boolean empty) {
				if(empty) {
					setGraphic(null);
				} else {
					author.setText(DatabaseManager.isChannelLoaded(video.getChannelId()) ? DatabaseManager.getChannel(video.getChannelId()).getTitle() : "Not found.");
					published.setText(sdf.format(video.getPublishDate()));
					comments.setText(video.getHttpCode() == 200 ? video.getComments()+" comments" : video.getHttpCode() == 403 ? "Comments Disabled" : "HTTP "+video.getHttpCode());
					if(video.getHttpCode() != 200) {
						comments.setStyle("-fx-color: red");
						vbox.setStyle("-fx-background-color: mistyrose");
					} else {
						comments.setStyle("");
						vbox.setStyle("");
					}
					setGraphic(vbox);
				}
			}
		});
		TableColumn<VideoType,Long> v_checkedCol = new TableColumn<>("Last checked");
		v_checkedCol.setCellValueFactory(new PropertyValueFactory<>("grabDate"));
		v_checkedCol.setCellFactory(col -> new TableCell<VideoType, Long>(){
			private SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy hh:mm a");
			public void updateItem(Long item, boolean empty) {
				if(empty || item == null) {
					setText(null);
				} else {
					setText(item > 0 ? sdf.format(new Date(item)) : "Never");
				}
				setAlignment(Pos.CENTER);
			}
		});
		v_table.getColumns().add(v_titleCol);
		v_table.getColumns().add(v_aboutCol);
		v_table.getColumns().add(v_checkedCol);
		grid.add(v_table, 1, 1);
		GridPane.setConstraints(v_table, 1, 1, 1, 1, HPos.CENTER, VPos.CENTER, Priority.ALWAYS, Priority.ALWAYS);
		
		ContextMenu v_menu = new ContextMenu();
		MenuItem v_open = new MenuItem("Open in Browser");
		v_open.setOnAction(e -> {
			VideoType v = v_table.getSelectionModel().getSelectedItem();
			CommentSuiteFX.openInBrowser(v.getYoutubeLink());
		});
		v_menu.getItems().add(v_open);
		v_table.setOnMouseClicked(e -> {
			if(e.isPopupTrigger()) {
				v_menu.show(this, e.getScreenX(), e.getScreenY());
			}
		});
		
		reloadTables();
		
		analytics = new Tab("Analytics");
		analytics.setClosable(false);
		
		VBox vbox2 = new VBox();
		analytics.setContent(vbox2);
		
		tabs.getTabs().addAll(items, analytics);
	}
	
	public void reloadTables() {
		gi_list.clear();
		v_list.clear();
		try {
			group = database.getGroup(group_id);
		} catch (SQLException e1) {
			e1.printStackTrace();
		}
		try {
			gi_list.addAll(database.getGitems(group_id, false));
			if(!gi_list.isEmpty())
				gi_label.setText(gi_list.size()+" items");
			else
				gi_label.setText("Add some items to this group.");
		} catch (SQLException e) {
			gi_label.setText(e.getClass().getName()+": "+e.getMessage());
			e.printStackTrace();
		}
		try {
			v_list.addAll(database.getVideos(group_id, false));
			if(!v_list.isEmpty())
				v_label.setText(v_list.size()+" videos");
			else
				v_label.setText("Refresh this group to get video data.");
		} catch (SQLException e) {
			v_label.setText(e.getClass().getName()+": "+e.getMessage());
			e.printStackTrace();
		}
	}
	
	public boolean isRefreshing() {
		return refreshing;
	}
	
	public void refresh() throws InterruptedException {
		refreshing = true;
		
		StackPane stack = new StackPane();
		stack.setStyle("-fx-background-color: linear-gradient(rgba(200,200,200,0.2), rgba(220,220,200,0.9), rgba(220,220,200,0.95), rgba(220,220,220,1))");
		stack.setMaxHeight(Double.MAX_VALUE);
		stack.setMaxWidth(Double.MAX_VALUE);
		getChildren().add(stack);
		
		GridPane grid = new GridPane();
		grid.setAlignment(Pos.CENTER);
		grid.setHgap(10);
		grid.setVgap(10);
		grid.setPadding(new Insets(25,25,25,25));
		stack.getChildren().add(grid);
		
		Label label = new Label("Progress");
		label.setAlignment(Pos.CENTER);
		label.setFont(Font.font("Tahoma", FontWeight.BOLD, 20));
		grid.add(label, 0, 0);
		
		Label status = new Label("Part 1 of 3. Checking for new videos.");
		status.setAlignment(Pos.CENTER);
		status.setFont(Font.font("Tahoma", FontWeight.NORMAL, 20));
		grid.add(status, 1, 0);
		
		Label status2 = new Label();
		grid.add(status2, 1, 1);
		
		NumberAxis xAxis = new NumberAxis();
		xAxis.setLabel("Elapsed Time (s)");
		NumberAxis yAxis = new NumberAxis();
		AreaChart<Number,Number> chart = new AreaChart<>(xAxis, yAxis);
		chart.setCreateSymbols(false);
		XYChart.Series<Number,Number> vseries = new XYChart.Series<>();
		vseries.setName("New Videos");
		XYChart.Series<Number,Number> cseries = new XYChart.Series<>();
		cseries.setName("New Comments");
		XYChart.Series<Number, Number> rseries = new XYChart.Series<>();
		rseries.setName("New Replies");
		chart.getData().add(vseries);
		chart.getData().add(cseries);
		chart.getData().add(rseries);
		chart.setPrefWidth(600);
		chart.setPrefHeight(300);
		grid.add(chart, 0, 2, 2, 1);
		
		HBox hbox = new HBox();
		hbox.setAlignment(Pos.CENTER_RIGHT);
		close = new Button("Close");
		close.setDisable(true);
		close.setOnAction(e -> {
			vseries.getData().clear();
			cseries.getData().clear();
			getChildren().remove(stack);
		});
		hbox.getChildren().add(close);
		grid.add(hbox, 0, 3, 2, 1);
		
		es = Executors.newCachedThreadPool();
		Task<Void> task = new Task<Void>() {
			private final int THREADS = 8;
			private final int COMMENT_INSERT_SIZE = 500;
			private ElapsedTime timer = new ElapsedTime();
			private long last_second = -1;
			private long new_comments = 0;
			private long thread_progress = 0;
			private long video_progress = 0;
			private long total_comments = 0;
			
			private List<String> existingVideoIds = new ArrayList<String>();
			private List<String> existingCommentIds = new ArrayList<String>();
			private List<String> existingChannelIds = new ArrayList<String>();
			private List<VideoGroup> existingVideoGroups = new ArrayList<VideoGroup>();
			private List<GitemType> existingGroupItems = new ArrayList<GitemType>();
			
			private Map<String, String> commentThreadIds = new HashMap<String, String>(); // <commentThreadId, videoId>
			private Map<String, Integer> commentThreadReplies;
			
			private List<VideoType> insertVideos = new ArrayList<VideoType>();
			private List<VideoType> updateVideos = new ArrayList<VideoType>();
			private List<ChannelType> insertChannels = new ArrayList<ChannelType>();
			private List<ChannelType> updateChannels = new ArrayList<ChannelType>();
			private List<VideoGroup> insertVideoGroups = new ArrayList<VideoGroup>();
			
			private List<String> insertedCommentIds = new ArrayList<String>();
			
			private List<String> gitemVideos = new ArrayList<String>();
			private List<GitemType> gitemChannels = new ArrayList<GitemType>();
			private List<GitemType> gitemPlaylists = new ArrayList<GitemType>();
			
			/*private boolean listContainsId(List<YoutubeObject> list, String youtubeId) {
				for(YoutubeObject obj : list) {
					if(obj.getId().equals(youtubeId)) 
						return true;
				}
				return false;
			}*/
			
			protected Void call() {
				try {
					timer.set();
					
					existingVideoIds.addAll(database.getVideoIds());
					existingCommentIds.addAll(database.getCommentIds(group_id));
					existingChannelIds.addAll(database.getChannelIds());
					existingVideoGroups.addAll(database.getVideoGroups());
					existingGroupItems.addAll(database.getGitems(group_id, false));
					commentThreadReplies = database.getCommentThreadReplyCounts(group_id);
					
					for(GitemType gi : existingGroupItems) {
						if(gi.type_id == 0) {
							VideoGroup vg = new VideoGroup(gi.getGitemId(), gi.getId());
							if(!existingVideoGroups.contains(vg)) {
								insertVideoGroups.add(vg);
							}
							gitemVideos.add(gi.getId());
						} else if(gi.type_id == 1) {
							gitemChannels.add(gi);
						} else if(gi.type_id == 2) {
							gitemPlaylists.add(gi);
						}
					}
					database.updateGitems(existingGroupItems);
					
					try {
						database.setAutoCommit(false);
						Platform.runLater(() -> {
							status.setText("Part 1 of 3. Finding new videos.");
							vseries.getData().add(new XYChart.Data<>(0, 0));
						});
						try {
							parseVideoItems(gitemVideos, -1);
							parseChannelItems(gitemChannels);
							parsePlaylistItems(gitemPlaylists);
						} catch (JsonSyntaxException | IOException e) {
							e.printStackTrace();
						}
						Platform.runLater(() -> {
							status.setText("Part 1 of 3. Inserting new videos (committing).");
						});
						database.insertVideos(insertVideos);
						database.updateVideos(updateVideos);
						database.insertVideoGroups(insertVideoGroups);
						database.commit();
						
						clearAll(existingVideoIds, existingVideoGroups);
						clearAll(insertVideos, updateVideos, insertVideoGroups);
						clearAll(gitemVideos, gitemChannels, gitemPlaylists);
						
						final long t = timer.getSeconds();
						Platform.runLater(() -> {
							cseries.getData().add(new XYChart.Data<>(t, 0));
							status.setText("Part 2 of 3. Finding new comments.");
						});
						List<String> videosInGroup = database.getVideoIds(group_id);
						ExecutorService es = Executors.newCachedThreadPool();
						for(int i=0; i < THREADS; i++) {
							final int offset = i;
							es.execute(() -> commentsThread(videosInGroup, offset));
						}
						es.shutdown();
						es.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);
						
						
						final long t3 = timer.getSeconds(), c = new_comments;
						Platform.runLater(() -> {
							cseries.getData().add(new XYChart.Data<>(t3, c));
							rseries.getData().add(new XYChart.Data<>(t3, c));
							status.setText("Part 2 of 3. Finding new comments (committing).");
						});
						database.commit();
						
						Platform.runLater(() -> {
							status.setText("Part 3 of 3. Finding new comment replies.");
						});
						es = Executors.newCachedThreadPool();
						final List<String> threads = commentThreadIds.keySet().stream().collect(Collectors.toList());
						for(int i=0; i < THREADS+8; i++) {
							final int offset = i;
							es.execute(() -> repliesThread(threads, offset));
						}
						es.shutdown();
						es.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);
						
						final long t2 = timer.getSeconds(), c2 = new_comments;
						Platform.runLater(() -> {
							rseries.getData().add(new XYChart.Data<>(t2, c2));
							status.setText("Part 3 of 3. Inserting channels and committing.");
						});
						database.insertChannels(insertChannels);
						database.updateChannels(updateChannels);
						database.commit();
					} catch (SQLException e) {
						e.printStackTrace();
					}
					clearAll(insertChannels, updateChannels, existingCommentIds, existingChannelIds);
					
					final String time = timer.getTimeString();
					Platform.runLater(() -> {
						status.setText("Complete.");
						status2.setText("Elapsed time: "+time);
						reloadTables();
					});
					database.setAutoCommit(true);
					close.setDisable(false);
					refreshing = false;
					CommentSuiteFX.app.setupWithManager(manager);
				} catch (Exception e) {
					e.printStackTrace();
				}
				return null;
			}
			
			private boolean videoListContainsId(List<VideoType> list, String id) {
				for(VideoType video : list)
					if(video.getId().equals(id)) return true;
				return false;
			}
			
			private void clearAll(Collection<?>... lists) {
				for(Collection<?> list : lists) {
					list.clear();
				}
			}
			
			private void parseChannelItems(List<GitemType> channels) throws JsonSyntaxException, IOException {
				for(GitemType gi : channels) {
					ChannelsList cl = data.getChannelsByChannelId(ChannelsList.PART_CONTENT_DETAILS, gi.getId(), ChannelsList.MAX_RESULTS, "");
					String uploadPlaylistId = cl.items[0].contentDetails.relatedPlaylists.uploads;
					handlePlaylist(uploadPlaylistId, gi.getGitemId());
				}
			}
			
			private void parsePlaylistItems(List<GitemType> playlists) throws JsonSyntaxException, IOException {
				for(GitemType gi : playlists) {
					handlePlaylist(gi.getId(), gi.getGitemId());
				}
			}
			
			private void handlePlaylist(final String playlistId, int gitem_id) throws JsonSyntaxException, IOException {
				PlaylistItemsList pil = null;
				String pageToken = "";
				List<String> videos = new ArrayList<String>();
				do {
					pil = data.getPlaylistItems(PlaylistItemsList.PART_SNIPPET, playlistId, PlaylistItemsList.MAX_RESULTS, pageToken);
					pageToken = pil.nextPageToken;
					for(PlaylistItemsList.Item item : pil.items) {
						if(item.hasSnippet()) {
							videos.add(item.snippet.resourceId.videoId);
						}
					}
				} while (pil.nextPageToken != null);
				parseVideoItems(videos, gitem_id);
			}
			
			private void parseVideoItems(List<String> videos, int gitem_id) throws JsonSyntaxException, IOException {
				for(int i=0; i<videos.size(); i += 50) {
					List<String> sublist = videos.subList(i, i+50 < videos.size() ? i+50 : videos.size());
					if(gitem_id != -1) {
						for(String v : sublist) {
							VideoGroup vg = new VideoGroup(gitem_id, v);
							if(!existingVideoGroups.contains(vg)) {
								if(!insertVideoGroups.contains(vg)) {
									insertVideoGroups.add(vg);
								}
							}
						}
					}
					String ids = sublist.stream().filter(id -> !videoListContainsId(insertVideos, id) && !videoListContainsId(updateVideos, id)).collect(Collectors.joining(","));
					handleVideos(ids);
				}
			}
			
			private void handleVideos(final String ids) throws JsonSyntaxException, IOException {
				System.out.println("Videos: "+ids);
				VideosList snip = data.getVideosById(VideosList.PART_SNIPPET, ids, VideosList.MAX_RESULTS, "");
				VideosList stats = data.getVideosById(VideosList.PART_STATISTICS, ids, VideosList.MAX_RESULTS, "");
				for(int i = 0; i<snip.items.length; i++) {
					VideosList.Item itemSnip = snip.items[i];
					VideosList.Item itemStat = stats.items[i];
					checkChannel(null, itemSnip, true);
					String videoId = itemSnip.id;
					String channelId = itemSnip.snippet.channelId;
					String title = itemSnip.snippet.title;
					String thumbUrl = itemSnip.snippet.thumbnails.medium.url.toString();
					String description = itemSnip.snippet.description;
					long views = itemStat.statistics.viewCount;
					long likes = itemStat.statistics.likeCount;
					long dislikes = itemStat.statistics.dislikeCount;
					long comments = itemStat.statistics.commentCount;
					VideoType video = new VideoType(videoId, channelId, title, thumbUrl, true, description, comments, likes, dislikes, views, itemSnip.snippet.publishedAt, new Date(), 200);
					if(!existingVideoIds.contains(itemSnip.id) && !videoListContainsId(insertVideos, itemSnip.id) && !videoListContainsId(updateVideos, itemSnip.id)) {
						insertVideos.add(video);
					} else {
						if(!videoListContainsId(updateVideos, itemSnip.id))
							updateVideos.add(video);
					}
				}
				final long t = timer.getSeconds(), v = insertVideos.size();
				Platform.runLater(() -> {
					vseries.getData().add(new XYChart.Data<>(t, v));
				});
			}
			
			private void commentsThread(final List<String> videos, final int offset) {
				int pos = offset;
				while(pos < videos.size()) {
					String videoId = videos.get(pos);
					try {
						getComments(videoId);
					} catch (JsonSyntaxException | SQLException e) {
						e.printStackTrace();
					}
					video_progress++;
					final long prog = video_progress;
					final long t1 = timer.getSeconds(), c1 = new_comments;
					Platform.runLater(() -> {
						status2.setText(total_comments+" total comments   "+prog+" / "+videos.size()+" videos");
						if(t1 > last_second+3) {
							last_second = t1;
							System.out.println(t1);
							Platform.runLater(() -> {
								cseries.getData().add(new XYChart.Data<>(t1, c1));
							});
						}
					});
					pos += THREADS;
				}
			}
			
			private void getComments(final String videoId) throws JsonSyntaxException, SQLException {
				List<CommentType> comments = new ArrayList<CommentType>();
				CommentThreadsList snippet = null;
				String snipToken = "";
				int fails = 0;
				do {
					if(comments.size() >= COMMENT_INSERT_SIZE) {
						submitComments(comments, cseries);
						comments.clear();
					}
					try {
						snippet = data.getCommentThreadsByVideoId(CommentThreadsList.PART_SNIPPET, videoId, CommentThreadsList.MAX_RESULTS, snipToken);
						snipToken = snippet.nextPageToken;
						total_comments += snippet.items.length;
						for(CommentThreadsList.Item item : snippet.items) {
							if(item.hasSnippet()) {
								String commentId = item.snippet.topLevelComment.id;
								boolean contains = commentThreadReplies.containsKey(commentId);
								if((!contains && item.snippet.totalReplyCount > 0) || (contains && item.snippet.totalReplyCount != commentThreadReplies.get(item.snippet.topLevelComment.id))) {
									commentThreadIds.put(commentId, videoId);
								}
								if(!existingCommentIds.contains(commentId)) {
									checkChannel(item.snippet.topLevelComment, null, false);
									CommentType comment = new CommentType(item);
									if(!comment.getChannelId().equals("")) {
										comments.add(comment);
									} else {
										System.out.println("Google Plus comment? "+item.snippet.topLevelComment.snippet.authorChannelUrl);
									}
								}
							}
						}
					} catch (IOException e) {
						fails++;
						if(e.getMessage().contains("HTTP response code")) {
							Pattern p = Pattern.compile("([0-9]{3}) for URL");
							Matcher m = p.matcher(e.getMessage());
							if(m.find()) {
								try {
									int code = Integer.parseInt(m.group(1));
									if(code == 400) { // Retry / Bad request.
										System.err.println("Bad Request (400): Retry #"+fails+"  http://youtu.be/"+videoId);
									} else if(code == 403) { // Comments Disabled or Forbidden
										System.err.println("Comments Disabled (403): http://youtu.be/"+videoId);
										database.updateVideoHttpCode(videoId, code);
										break;
									} else if(code == 404) { // Not found.
										System.err.println("Not found (404): http://youtu.be/"+videoId);
										database.updateVideoHttpCode(videoId, code);
										break;
									} else { // Unknown error.
										System.err.println("Unknown Error ("+code+"): http://youtu.be/"+videoId);
										database.updateVideoHttpCode(videoId, code);
										break;
									}
								} catch (SQLException e1) {
									e1.printStackTrace();
								}
							}
						}
					}
				} while ((snippet == null || snippet.nextPageToken != null) && fails < 5);
				if(comments.size() > 0) {
					submitComments(comments, cseries);
					comments.clear();
				}
			}
			
			private void repliesThread(final List<String> threads, int offset) {
				int pos = offset;
				while(pos < threads.size()) {
					final String threadId = threads.get(pos);
					try {
						getReplies(threadId, commentThreadIds.get(threadId));
						thread_progress++;
						final long prog = thread_progress;
						final long t1 = timer.getSeconds(), c1 = new_comments;
						Platform.runLater(() -> {
							status2.setText(total_comments+" total comments   "+prog+" / "+threads.size()+" comment threads");
							if(t1 > last_second+3) {
								last_second = t1;
								System.out.println(t1);
								Platform.runLater(() -> {
									rseries.getData().add(new XYChart.Data<>(t1, c1));
								});
							}
						});
					} catch (JsonSyntaxException e) {
						e.printStackTrace();
					} catch (Throwable e) {
						System.out.println("Something broke. "+threadId);
						e.printStackTrace();
					}
					pos += THREADS+8;
				}
			}
			
			private void getReplies(final String threadId, final String videoId) throws JsonSyntaxException, SQLException {
				List<CommentType> replies = new ArrayList<CommentType>();
				CommentsList cl = null;
				String pageToken = "";
				int fails = 0;
				do {
					if(replies.size() >= COMMENT_INSERT_SIZE) {
						submitComments(replies, rseries);
						replies.clear();
					}
					try {
						cl = data.getCommentsByParentId(threadId, CommentsList.MAX_RESULTS, pageToken);
						total_comments += cl.items.length;
						pageToken = cl.nextPageToken;
						for(CommentsList.Item reply : cl.items) {
							if(!existingCommentIds.contains(reply.id)) {
								checkChannel(reply, null, false);
								CommentType comment = new CommentType(reply, videoId);
								if(!comment.getChannelId().equals("")) {
									replies.add(comment);
								} else {
									System.out.println("Google Plus comment? "+reply.snippet.authorChannelUrl);
								}
							}
						}
					} catch (IOException e) {
						fails++;
					}
				} while (cl.nextPageToken != null && fails < 5);
				if(replies.size() > 0) {
					submitComments(replies, rseries);
					replies.clear();
				}
			}
			
			// UC9RM-iSvTu1uPJb8X5yp3EQ
			
			private void submitComments(List<CommentType> comments, final XYChart.Series<Number,Number> series) throws SQLException {
				if(comments.size() > 0) {
					new_comments += comments.size();
					database.insertComments(comments.stream()
							.filter(ct -> !insertedCommentIds.contains(ct.getId()) && !existingCommentIds.contains(ct.getId()))
							.peek(ct -> insertedCommentIds.add(ct.getId()))
							.collect(Collectors.toList()));
					final long t2 = timer.getSeconds(), c = new_comments;
					if(t2 > last_second+3) {
						last_second = t2;
						Platform.runLater(() -> {
							series.getData().add(new XYChart.Data<>(t2, c));
						});
					}
				}
			}
			
			private void checkChannel(CommentsList.Item comment, VideosList.Item video, boolean fetchThumb) {
				String channelId = null;
				if(comment != null && comment.snippet != null && comment.snippet.authorChannelId != null && comment.snippet.authorChannelId.value != null) {
					channelId = comment.snippet.authorChannelId.value;
				}
				if(video != null) {
					channelId = video.snippet.channelId;
				}
				ChannelType channel = null;
				if(channelId != null) {
					if(!existingChannelIds.contains(channelId)) {
						if(comment != null) {
							channel = new ChannelType(comment, fetchThumb);
						} else if(video != null) {
							try {
								ChannelsList cl = data.getChannelsByChannelId(ChannelsList.PART_SNIPPET, channelId, 1, "");
								ChannelsList.Item item = cl.items[0];
								channel = new ChannelType(item, true);
							} catch (JsonSyntaxException | IOException e) {
								e.printStackTrace();
							}
						}
					}
					if(channel != null) {
						if(!existingChannelIds.contains(channelId)) {
							if(!insertChannels.contains(channel)) {
								insertChannels.add(channel);
							}
						} else {
							if(!updateChannels.contains(channel)) {
								updateChannels.add(channel);
							}
						}
					}
				}
			}
		};
		es.execute(task);
		es.shutdown();
	}
}
