package io.mattw.youtube.commentsuite.fxml;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonWriter;
import io.mattw.youtube.commentsuite.Cleanable;
import io.mattw.youtube.commentsuite.FXMLSuite;
import io.mattw.youtube.commentsuite.ImageCache;
import io.mattw.youtube.commentsuite.db.*;
import io.mattw.youtube.commentsuite.util.ExecutorGroup;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.TextArea;
import javafx.scene.layout.VBox;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.awt.*;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.LinkedBlockingQueue;

import static javafx.application.Platform.runLater;

/**
 * This modal allows the full export of all comments in a query to the specified format
 * <p>
 * exports/
 * yyyy.MM.dd HH.mm.SS/
 * searchSettings.json
 * videoId1-meta.json
 * videoId1-comments.json
 * videoId2-meta.json
 * videoId2-comments.json
 * ...
 *
 * @author mattwright324
 * @see SearchComments
 */
public class SCExportModal extends VBox implements Cleanable, ImageCache {

    private static final Logger logger = LogManager.getLogger();

    private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy.MM.dd HH.mm.ss");
    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private static final JsonParser jsonParser = new JsonParser();
    private static final String prettyFlattenedExample = gson.toJson(jsonParser.parse(
            "[{\"type\":\"comment\"},{\"type\":\"comment\"},{\"type\":\"reply\"},{\"type\":\"reply\"},{\"type\":\"comment\"}]"));
    private static final String prettyCondensedExample = gson.toJson(jsonParser.parse(
            "[{\"type\":\"comment\"},{\"type\":\"comment\", replies:[{\"type\":\"reply\"},{\"type\":\"reply\"}]},{\"type\":\"comment\"}]"));

    private static final File exportsFolder = new File("exports/");
    private static final String searchSettingsFileName = "searchSettings.json";

    @FXML private Label errorMsg;

    @FXML private RadioButton radioCondensed, radioFlattened;
    @FXML private TextArea exportModeExample;

    @FXML private Button btnClose;
    @FXML private Button btnStop;
    @FXML private Button btnSubmit;

    private CommentQuery commentQuery;
    private CommentDatabase database;

    private boolean quitExport = false;

    public SCExportModal() {
        logger.debug("Initialize SCExportModal");

        database = FXMLSuite.getDatabase();

        FXMLLoader loader = new FXMLLoader(getClass().getResource("SCExportModal.fxml"));
        loader.setController(this);
        loader.setRoot(this);
        try {
            loader.load();

            cleanUp();

            radioFlattened.setOnAction(ae -> runLater(() -> exportModeExample.setText(prettyFlattenedExample)));
            radioCondensed.setOnAction(ae -> runLater(() -> exportModeExample.setText(prettyCondensedExample)));

            radioFlattened.fire();

            btnStop.setOnAction(ae -> {
                quitExport = true;

                runLater(() -> {
                    btnStop.setDisable(true);
                });
            });

            btnSubmit.setOnAction(ae -> {
                runLater(() -> {
                    btnSubmit.setVisible(false);
                    btnSubmit.setManaged(false);

                    btnStop.setVisible(true);
                    btnStop.setManaged(true);

                    btnClose.setVisible(false);
                    btnClose.setManaged(false);
                });

                final boolean flattenedMode = radioFlattened.isSelected();

                new Thread(() -> {
                    LocalDateTime now = LocalDateTime.now();

                    File thisExportFolder = new File(exportsFolder, formatter.format(now) + "/");
                    thisExportFolder.mkdirs();
                    File searchSettings = new File(thisExportFolder, searchSettingsFileName);

                    try (FileWriter writer = new FileWriter(searchSettings)) {
                        logger.debug("Writing file {}", searchSettings.getName());

                        gson.toJson(commentQuery, writer);

                        writer.flush();
                    } catch (Exception e) {
                        logger.error("Failed to write json file(s)", e);

                        runLater(() -> setError("Failed to export."));
                    }

                    try {
                        Set<String> uniqueVideoIds = commentQuery.getUniqueVideoIds();
                        LinkedBlockingQueue<String> videoIdQueue = new LinkedBlockingQueue<>(uniqueVideoIds);

                        // Threads to speed up export a bit, condensed still will take a long time.
                        ExecutorGroup exportGroup = new ExecutorGroup(10);
                        exportGroup.submitAndShutdown(() -> {
                            String videoId;
                            while (!videoIdQueue.isEmpty() && !quitExport) {
                                videoId = videoIdQueue.poll();

                                File videoFile = new File(thisExportFolder, String.format("%s-meta.json", videoId));

                                YouTubeVideo video = null;
                                try (FileWriter writer = new FileWriter(videoFile)) {
                                    video = database.getVideo(videoId);
                                    video.setAuthor(database.getChannel(video.getChannelId()));
                                    video.prepForExport();

                                    logger.debug("Writing file {}", videoFile.getName());

                                    gson.toJson(video, writer);

                                    writer.flush();
                                } catch (SQLException e) {
                                    logger.error("Error while grabbing video data", e);

                                    runLater(() -> setError("Error during export, check logs."));
                                } catch (Exception e) {
                                    logger.error("Failed to write json file(s)", e);

                                    runLater(() -> setError("Error during export, check logs."));
                                }

                                if (video != null) {
                                    File commentsFile = new File(thisExportFolder, String.format("%s-comments.json", videoId));
                                    List<YouTubeVideo> videoList = Collections.singletonList(video);

                                    // Duplicate a new query object because it isn't threadsafe.
                                    // ExecutorGroup should make export faster
                                    CommentQuery localQuery = database.commentQuery()
                                            .setGroup(commentQuery.getGroup())
                                            .setGroupItem(commentQuery.getGroupItem())
                                            .setCommentsType(commentQuery.getCommentsType())
                                            .setVideos(commentQuery.getVideos())
                                            .setNameLike(commentQuery.getNameLike())
                                            .setOrder(commentQuery.getOrder())
                                            .setTextLike(commentQuery.getTextLike())
                                            .setDateFrom(commentQuery.getDateFrom())
                                            .setDateTo(commentQuery.getDateTo());

                                    if (!flattenedMode && commentQuery.getCommentsType() == CommentQuery.CommentsType.ALL) {
                                        // When condensed, we want base comments to be first.
                                        // We'll grab replies later if the replyCount > 0
                                        localQuery.setCommentsType(CommentQuery.CommentsType.COMMENTS_ONLY);
                                    }

                                    try (FileWriter writer = new FileWriter(commentsFile);
                                         JsonWriter jsonWriter = new JsonWriter(writer)) {
                                        logger.debug("Writing file {}", commentsFile.getName());

                                        jsonWriter.beginArray();

                                        try (NamedParameterStatement namedParamStatement = localQuery
                                                .setVideos(Optional.ofNullable(videoList))
                                                .toStatement();
                                             ResultSet resultSet = namedParamStatement.executeQuery()) {

                                            // starting with flattened mode (easier)
                                            while (resultSet.next() && !quitExport) {
                                                YouTubeComment comment = database.resultSetToComment(resultSet);
                                                comment.setAuthor(database.getChannel(comment.getChannelId()));
                                                comment.prepForExport();

                                                if (!flattenedMode && comment.getReplyCount() > 0 && !comment.isReply()) {
                                                    List<YouTubeComment> replyList = database.getCommentTree(comment.getId());
                                                    for (YouTubeComment reply : replyList) {
                                                        reply.setAuthor(database.getChannel(reply.getChannelId()));
                                                    }

                                                    comment.setReplies(replyList);
                                                }

                                                gson.toJson(gson.toJsonTree(comment), jsonWriter);
                                            }
                                        }

                                        jsonWriter.endArray();

                                    } catch (SQLException e) {
                                        logger.error("Error while grabbing comments", e);

                                        runLater(() -> setError("Error during export, check logs."));
                                    } catch (IOException e) {
                                        logger.error("Failed to write json file", e);
                                    }
                                }
                            }
                        });
                        exportGroup.await();
                    } catch (SQLException | InterruptedException e) {
                        logger.error("Failed to get unique videoIds", e);
                    }

                    try {
                        logger.debug("Opening folder {}", thisExportFolder.getAbsolutePath());

                        Desktop.getDesktop().open(thisExportFolder);
                    } catch (IOException e) {
                        logger.warn("Failed to open export folder");
                    } finally {
                        runLater(() -> {
                            btnStop.setVisible(false);
                            btnStop.setManaged(false);

                            btnClose.setVisible(true);
                            btnClose.setManaged(true);

                            if(!quitExport) {
                                btnClose.fire();
                            }
                        });
                    }
                }).start();
            });
        } catch (IOException e) {
            logger.error(e);
            e.printStackTrace();
        }
    }

    void setError(String error) {
        errorMsg.setText(error);
        errorMsg.setVisible(true);
        errorMsg.setManaged(true);
    }

    public void withQuery(CommentQuery commentQuery) {
        this.commentQuery = commentQuery;

        CommentQuery.CommentsType type = commentQuery.getCommentsType();

        runLater(() -> {
            boolean condensable = type == CommentQuery.CommentsType.ALL
                    && StringUtils.isEmpty(commentQuery.getNameLike())
                    && StringUtils.isEmpty(commentQuery.getTextLike());

            radioCondensed.setDisable(!condensable);

            if (!condensable) {
                radioFlattened.fire();
            }
        });
    }

    public Button getBtnClose() {
        return btnClose;
    }

    public Button getBtnSubmit() {
        return btnSubmit;
    }

    @Override
    public void cleanUp() {
        errorMsg.setVisible(false);
        errorMsg.setManaged(false);

        btnSubmit.setVisible(true);
        btnSubmit.setManaged(true);

        btnStop.setVisible(false);
        btnStop.setManaged(false);

        btnClose.setVisible(true);
        btnClose.setManaged(true);
    }
}
