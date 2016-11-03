package org.sitoolkit.wt.gui.pres;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.util.List;
import java.util.Optional;
import java.util.ResourceBundle;

import org.sitoolkit.wt.gui.domain.JettyConsoleListener;
import org.sitoolkit.wt.gui.domain.ProjectState;
import org.sitoolkit.wt.gui.domain.ProjectState.State;
import org.sitoolkit.wt.gui.domain.SitWtDebugConsoleListener;
import org.sitoolkit.wt.gui.domain.SitWtRuntimeUtils;
import org.sitoolkit.wt.gui.infra.ConversationProcess;
import org.sitoolkit.wt.gui.infra.ExecutorContainer;
import org.sitoolkit.wt.gui.infra.FxContext;
import org.sitoolkit.wt.gui.infra.PropertyManager;
import org.sitoolkit.wt.gui.infra.StageResizer;
import org.sitoolkit.wt.gui.infra.StrUtils;
import org.sitoolkit.wt.gui.infra.SystemUtils;
import org.sitoolkit.wt.gui.infra.TextAreaConsole;
import org.sitoolkit.wt.gui.infra.UnExpectedException;
import org.sitoolkit.wt.gui.infra.maven.MavenUtils;

import javafx.application.Platform;
import javafx.beans.value.ObservableBooleanValue;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.ToolBar;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;

public class AppController implements Initializable {

    @FXML
    private Menu baseUrlMenu;

    @FXML
    private ToolBar startGroup;

    @FXML
    private ToolBar runningGroup;

    @FXML
    private ToolBar browsingGroup;

    @FXML
    private ToolBar debugGroup;

    @FXML
    private TextArea console;

    @FXML
    private Button runButton;

    @FXML
    private ChoiceBox<String> browserChoice;

    @FXML
    private ComboBox<String> baseUrlCombo;

    @FXML
    private Button pauseButton;

    @FXML
    private TextField stepNoText;

    @FXML
    private Button quitButton;

    @FXML
    private Button backButton;

    @FXML
    private Button forwardButton;

    @FXML
    private Button exportButton;

    @FXML
    private Button toggleButton;

    @FXML
    private FileTreeController fileTreeController;

    @FXML
    private MenuItem sampleRunMenu;

    @FXML
    private MenuItem sampleStopMenu;

    private ConversationProcess conversationProcess = new ConversationProcess();

    private ProjectState projectState = new ProjectState();

    private File pomFile = new File("pom.xml");

    private SitWtDebugConsoleListener mavenConsoleListener = new SitWtDebugConsoleListener();

    @Override
    public void initialize(URL location, ResourceBundle resources) {

    	if (Boolean.getBoolean("checkUpdate")) {
            ExecutorContainer.get().execute(() -> UpdateChecker.checkAndInstall());
    	}

        setVisible(startGroup, projectState.isLoaded());
        setVisible(runningGroup, projectState.isRunning());
        setVisible(browsingGroup, projectState.isBrowsing());
        setVisible(debugGroup, projectState.isDebugging());

        sampleRunMenu.disableProperty().bind(projectState.isLoaded().not());

        browserChoice.getItems().addAll(SystemUtils.getBrowsers());

        // TODO プロジェクトの初期化判定はpom.xml内にSIT-WTの設定があること
        projectState.setState(pomFile.exists() ? State.LOADED : State.NOT_LOADED);
        if (pomFile.exists()) {
            loadProject(pomFile);
        } else {
            addMsg("[プロジェクト]>[新規作成]からプロジェクトを作成するフォルダを選択してください。");
        }

    }

    public void destroy() {
        conversationProcess.destroy();
        PropertyManager.get().setBaseUrls(baseUrlCombo.getItems());

        File sampleDir = new File(pomFile.getParent(), "sample");
        if (sampleDir.exists()) {
            conversationProcess.start(new TextAreaConsole(console), sampleDir,
                    MavenUtils.getCommand(), "jetty:stop");
        }

    }

    private void setVisible(Node node, ObservableBooleanValue visible) {
        node.visibleProperty().bind(visible);
        node.managedProperty().bind(visible);
    }

    @FXML
    public void createProject() {
        DirectoryChooser dirChooser = new DirectoryChooser();
        dirChooser.setTitle("プロジェクトを作成するフォルダを選択してください。");
        dirChooser.setInitialDirectory(new File("."));

        File projectDir = dirChooser.showDialog(FxContext.getPrimaryStage());

        if (projectDir == null) {
            return;
        }

        pomFile = new File(projectDir, "pom.xml");
        if (pomFile.exists()) {
            addMsg("プロジェクトは既に存在します。");
            return;
        }

        createPom();

    }

    private void createPom() {
        try {
            URL pomUrl = getClass().getResource("/distribution-pom.xml");
            Files.copy(pomUrl.openStream(), pomFile.toPath());
        } catch (IOException e) {
            throw new UnExpectedException(e);
        }

        projectState.isLoaded().set(pomFile.exists());

        if (pomFile.exists()) {

            loadProject(pomFile);
            addMsg("プロジェクトを作成しました。");
        }
    }

    @FXML
    public void openProject() {
        DirectoryChooser dirChooser = new DirectoryChooser();
        dirChooser.setTitle("プロジェクトのpom.xmlがあるフォルダを選択してください。");
        dirChooser.setInitialDirectory(new File("."));

        File projectDir = dirChooser.showDialog(FxContext.getPrimaryStage());

        if (projectDir == null) {
            return;
        }

        pomFile = new File(projectDir, "pom.xml");

        if (pomFile.exists()) {

            loadProject(pomFile);
            addMsg("プロジェクトを開きました。");

        } else {

            Alert alert = new Alert(AlertType.CONFIRMATION);
            alert.setContentText("pom.xmlがありません。作成しますか？");

            Optional<ButtonType> answer = alert.showAndWait();
            if (answer.get() == ButtonType.OK) {
                createPom();
            }

        }
    }

    private void loadProject(File pomFile) {

        fileTreeController.setFileTreeRoot(pomFile.getParentFile());
        PropertyManager.get().load(pomFile.getAbsoluteFile().getParentFile());

        List<String> baseUrls = PropertyManager.get().getBaseUrls();
        if (!baseUrls.isEmpty()) {
            baseUrlCombo.getItems().addAll(baseUrls);
            baseUrlCombo.setValue(baseUrls.get(0));
        }

        SitWtRuntimeUtils.loadSitWtClasspath(pomFile);
        projectState.reset();
    }

    @FXML
    public void runSample() {
        projectState.setState(State.LOCKING);
        sampleRunMenu.setVisible(false);
        startMsg("サンプルWebサイトを起動します。");

        conversationProcess.start(new TextAreaConsole(console),
                pomFile.getAbsoluteFile().getParentFile(), SitWtRuntimeUtils.buildSampleCommand());

        conversationProcess.onExit(exitCode -> {

            File sampledir = new File(pomFile.getParent(), "sample");
            if (!sampledir.exists()) {
                sampledir.mkdirs();
            }

            JettyConsoleListener listener = new JettyConsoleListener();
            conversationProcess.start(new TextAreaConsole(console, listener), sampledir,
                    MavenUtils.getCommand());

            ExecutorContainer.get().execute(() -> {
                if (listener.isSuccess()) {
                    addMsg("サンプルWebサイトを起動しました。");
                    // TODO URLの動的取得
                    baseUrlCombo.setValue("http://localhost:8280");
                    sampleStopMenu.setVisible(true);
                } else {
                    addMsg("サンプルWebサイトの起動に失敗しました。");
                    sampleRunMenu.setVisible(true);
                }
                Platform.runLater(() -> fileTreeController.refresh()); 
                projectState.reset();
            });

        });
    }

    @FXML
    public void stopSample() {
        sampleStopMenu.setVisible(false);
        startMsg("サンプルWebサイトを停止します。");
        File sampledir = new File(pomFile.getParent(), "sample");
        conversationProcess.start(new TextAreaConsole(console), sampledir, MavenUtils.getCommand(),
                "jetty:stop");

        conversationProcess.onExit(exitCode -> {

            Platform.runLater(() -> {
                sampleRunMenu.setVisible(true);
                addMsg("サンプルWebサイトを停止しました。");
            });
        });
    }

    @FXML
    public void run() {
    	runTest(false, false);
    }
    
    @FXML
    public void debug() {
    	runTest(true, false);
    }

    @FXML
    public void runParallel() {
    	runTest(false, true);
    }

    private void runTest(boolean isDebug, boolean isParallel) {
        projectState.setState(isDebug ? State.DEBUGGING : State.RUNNING);

        String testedClasses = SitWtRuntimeUtils
                .findTestedClasses(fileTreeController.getSelectedFiles());

        if (StrUtils.isEmpty(testedClasses)) {
            Alert alert = new Alert(AlertType.INFORMATION);
            alert.setTitle("");
            alert.setContentText("");
            alert.setHeaderText("実行するテストスクリプトを選択してください。");
            alert.show();
            projectState.reset();
            return;
        }

        startMsg("テストを実行します。");

        String baseUrl = baseUrlCombo.getValue();
        addBaseUrl(baseUrl);

        List<String> command = SitWtRuntimeUtils.buildSingleTestCommand(
                fileTreeController.getSelectedFiles(), isDebug, isParallel,
                browserChoice.getSelectionModel().getSelectedItem(), baseUrl);

        conversationProcess.start(new TextAreaConsole(console, mavenConsoleListener),
                pomFile.getAbsoluteFile().getParentFile(), command);

        conversationProcess.onExit(exitCode -> {
            projectState.reset();
            Platform.runLater(() -> addMsg("テストを終了します。"));
        });
    }

    private void addBaseUrl(String baseUrl) {
        List<String> items = baseUrlCombo.getItems();
        int limit = PropertyManager.get().getBaseUrlLimit();

        if (!items.contains(baseUrl)) {
            items.add(0, baseUrl);
        }

        if (items.size() > limit) {
            items.remove(limit);
        }
        baseUrlCombo.setValue(baseUrl);
    }

    @FXML
    public void page2script() {
        List<String> command = SitWtRuntimeUtils.buildPage2ScriptCommand(browserChoice.getValue(),
                baseUrlCombo.getValue());

        conversationProcess.start(new TextAreaConsole(console, mavenConsoleListener),
                pomFile.getAbsoluteFile().getParentFile(), command);

        addMsg("ブラウザでページを表示した状態で「スクリプト生成」ボタンをクリックしてください。");

        projectState.setState(State.BROWSING);
        conversationProcess.onExit(exitCode -> {
            projectState.reset();
        });
    }

    @FXML
    public void quitBrowsing() {
        conversationProcess.input("q");
    }

    @FXML
    public void ope2script() {
        List<String> command = SitWtRuntimeUtils.buildOpe2ScriptCommand();

        conversationProcess.start(new TextAreaConsole(console, mavenConsoleListener),
                pomFile.getAbsoluteFile().getParentFile(), command);
    }

    @FXML
    public void pause() {
        if (mavenConsoleListener.isPausing()) {
            pauseButton.setText("\uE034");

            String stepNo = stepNoText.getText();
            if (!StrUtils.isEmpty(stepNo)) {
                conversationProcess.input("!" + stepNo);
                conversationProcess.input("#" + stepNo);
            }

            conversationProcess.input("s");
        } else {
            pauseButton.setText("\uE037");
            conversationProcess.input("");
        }
    }

    @FXML
    public void back() {
        conversationProcess.input("b");
    }

    @FXML
    public void forward() {
        conversationProcess.input("f");
    }

    @FXML
    public void export() {
        conversationProcess.input("e");
        fileTreeController.refresh();
    }

    @FXML
    public void openScript() {
        conversationProcess.input("o");
    }

    @FXML
    public void quit() {
        conversationProcess.destroy();
        projectState.reset();
    }

    private double stageHeight;

    private double stageWidth;

    @FXML
    public void toggleWindow() {
        Stage primaryStage = FxContext.getPrimaryStage();

        if ("\uE5D0".equals(toggleButton.getText())) {
            StageResizer.resize(primaryStage, stageWidth, stageHeight);
            toggleButton.setText("\uE5D1");
            toggleButton.getTooltip().setText("ウィンドウを縮小します");

        } else {

            stageHeight = primaryStage.getHeight();
            stageWidth = primaryStage.getWidth();
            // TODO コンソールのサイズ設定
            StageResizer.resize(primaryStage, 600, 90);

            toggleButton.setText("\uE5D0");
            toggleButton.getTooltip().setText("ウィンドウを拡大します");
        }
    }

    private void startMsg(String msg) {
        if (StrUtils.isNotEmpty(console.getText())) {
            console.appendText(System.lineSeparator());
            console.appendText(System.lineSeparator());
            console.appendText(System.lineSeparator());
        }
        addMsg(msg);
    }

    private void addMsg(String msg) {
        console.appendText(msg);
        console.appendText(System.lineSeparator());
    }

}
