package main;

import java.util.*;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.beans.*;
import javax.imageio.ImageIO;

public class Main {

    //UI Stuff
    private JLabel dateLabel;
    private JFrame frame = new JFrame("Stock Visualizer");
    private JPanel viewPane = new JPanel(new GridBagLayout());
    private JPanel graphPane = new JPanel(new GridBagLayout());

    public static void main(String[] args) {
        new Main();
    }

    //Initializes the UI
    public Main(){
        Db.readDb();

        /*try{//Windows does not respect setting the button colour
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch(Exception unused){}//Too bad, default look it is*/

        try{
            frame.setIconImage(ImageIO.read(this.getClass().getResource("icon.png")));
        } catch(Exception unused){}//Too bad, no icon

        //Save before exit
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.addWindowListener(new WindowAdapter(){
            public void windowClosing(WindowEvent e){
                Db.writeDb();
            };
        });

        //Set up UI
        drawViewPane();
        drawGraphPane();

        JTabbedPane mainPane = new JTabbedPane();
        mainPane.addTab("Add items", createAddPane());
        mainPane.addTab("Edit items", viewPane);
        mainPane.addTab("Graph items", graphPane);
        mainPane.addTab("Config", createCfgPane());

        frame.add(mainPane);
        frame.pack();//Automatically sets the size, probably not optimal...
        frame.setVisible(true);
    }

    //Convenience method for creating the constraints
    //Aditional manual configuration will be optionally needed to set the weights
    public static GridBagConstraints createGridBagConstraints(int xLoc, int xWidth, int yLoc, int yHeight){
        GridBagConstraints returnable = new GridBagConstraints();
        returnable.gridx = xLoc;
        returnable.gridwidth = xWidth;
        returnable.gridy = yLoc;
        returnable.gridheight = yHeight;
        return returnable;
    };

    //This panel is used to create new entries
    private JPanel createAddPane(){

        EditPanel createPane = new EditPanel(frame);

        dateLabel = new JLabel(Db.datesToLabel());
        GridBagConstraints dateLabelC = createGridBagConstraints(0, 1, 8, 1);
        createPane.add(dateLabel, dateLabelC);

        JButton saveButton = new JButton("Create");
        saveButton.addActionListener(e -> {
            Entry created = createPane.tryConstruct(false);
            if (created != null){
                Db.createEntry(created);

                //Update UI
                dateLabel.setText(Db.datesToLabel());
                frame.getContentPane().validate();
                frame.getContentPane().repaint();
                //Both other panes need to be redrawn
                drawGraphPane();
                drawViewPane();
            }
        });

        //Button unique to this tab
        JButton updateButton = new JButton("Update all prices");
        updateButton.addActionListener(e -> {
            //Blocks others
            JDialog progressDialog = new JDialog(frame, "Updating prices...", Dialog.ModalityType.APPLICATION_MODAL);

            JProgressBar progressBar = new JProgressBar(0, Math.max(Db.getNumEntries(), 1));
            progressBar.setValue(0);
            progressBar.setString("Initializing");
            progressBar.setStringPainted(true);

            PriceWorker priceWorker = new PriceWorker();
            priceWorker.addPropertyChangeListener(new PropertyChangeListener(){
                public void propertyChange(PropertyChangeEvent evt){
                    if(evt.getPropertyName() != "progress"){
                        return;
                    }
                    int progress = (Integer) evt.getNewValue();
                    progressBar.setValue(progress);
                    if(progress < Db.getNumEntries()){
                        progressBar.setString(Db.getEntry(progress).getTicker());
                    }
                    else{
                        progressBar.setString("Done");
                        try{
                            ArrayList<String> result = priceWorker.get();
                            boolean resultPassed = result.size() == 0;
                            String msg = "";
                            if(resultPassed){
                                msg = "All prices updated successfully.";
                            }
                            else{
                                msg = "The following tickers failed to update: " + String.join(", ", result);
                            }
                            JOptionPane.showMessageDialog(frame, msg, (resultPassed ? "Price update successful" : "Price update unsuccessful"), (resultPassed ? JOptionPane.INFORMATION_MESSAGE : JOptionPane.ERROR_MESSAGE));
                        } catch(Exception unused){
                            JOptionPane.showMessageDialog(frame, "Reading progress returned an exception.", "Price update unknown", JOptionPane.ERROR_MESSAGE);
                        }

                        Db.updatePriceTime();
                        dateLabel.setText(Db.datesToLabel());
                        drawViewPane();
                        progressDialog.dispose();
                    }
                };
            });
            progressDialog.add(progressBar);
            priceWorker.execute();
            progressDialog.pack();
            progressDialog.setVisible(true);
        });

        //Panel for the create/update buttons
        JPanel buttonPanel = new JPanel(new GridLayout(0, 2));
        buttonPanel.add(updateButton);
        buttonPanel.add(saveButton);

        GridBagConstraints buttonPanelC = createGridBagConstraints(1, 1, 8, 1);
        buttonPanelC.anchor = GridBagConstraints.SOUTHEAST;
        createPane.add(buttonPanel, buttonPanelC);

        return createPane;
    }

    //Draws the pane used for graphing in addition to the one that holds the graph
    private void drawGraphPane(){
        graphPane.removeAll();

        JPanel graphPaneTag = new JPanel(new GridLayout(0, Db.maxValsForTag() + 1));

        JScrollPane graphScrollPane = new JScrollPane(graphPaneTag);
        GridBagConstraints graphScrollPaneC = createGridBagConstraints(0, 2, 0, 1);
        graphScrollPaneC.weightx = 0.5;
        graphScrollPaneC.weighty = 0.5;
        graphScrollPaneC.fill = GridBagConstraints.BOTH;
        graphPane.add(graphScrollPane, graphScrollPaneC);

        JButton graphButton = new JButton("Graph");

        //Launch the graph
        graphButton.addActionListener(e -> {
            int length = Db.maxValsForTag()+1;
            ArrayList<String> includeValues = new ArrayList<>();
            ArrayList<String> includeValueTags = new ArrayList<>();
            ArrayList<String> removeValues = new ArrayList<>();
            ArrayList<String> removeValueTags = new ArrayList<>();
            String axisTag = "";

            //Look at the buttons to see what has been selected
            for(int i = 0; i < Db.getTags().length * length; i++){
                JButton buttonToExamine = (JButton) graphPaneTag.getComponent(i);
                Color buttonColour = buttonToExamine.getBackground();
                if(i % length == 0 && buttonColour == Color.GREEN){
                    axisTag = buttonToExamine.getText();
                }
                else if(buttonColour == Color.GREEN){//Includes
                    includeValues.add(buttonToExamine.getText());
                    includeValueTags.add(((JButton) graphPaneTag.getComponent(i - i % length)).getText());
                }
                else if(buttonColour == Color.RED){//Excludes
                    removeValues.add(buttonToExamine.getText());
                    removeValueTags.add(((JButton) graphPaneTag.getComponent(i - i % length)).getText());
                }
                else{
                }
            }
            Map<String, Float> graphable = Db.findGraphables(axisTag, includeValues, includeValueTags, removeValues, removeValueTags);
            if(graphable == null){
                JOptionPane.showMessageDialog(frame, "No entries matched the criteria.", "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }
            else{//Valid graph
                JFrame graphFrame = new JFrame("Pie graph");
                try{
                    graphFrame.setIconImage(ImageIO.read(this.getClass().getResource("icon.png")));
                } catch(Exception unused){}//Too bad, no icon

                graphFrame.setPreferredSize(new Dimension(600, 600));

                JPanel piePane = new JPanel(new GridBagLayout());

                Pie myPie = new Pie(graphable);
                GridBagConstraints myPieC = createGridBagConstraints(0, 1, 1, 1);
                myPieC.fill = GridBagConstraints.BOTH;
                myPieC.weightx = 0.5;
                myPieC.weighty = 0.9;
                piePane.add(myPie, myPieC);

                JLabel graphTitle = new JLabel(String.format("Category: %s - Total: $%,.2f %s", "".equals(axisTag) ? "None" : axisTag, myPie.getTotal(), Db.getGraphCurrency().toString()));
                GridBagConstraints graphTitleC = createGridBagConstraints(0, 1, 0, 1);
                piePane.add(graphTitle, graphTitleC);


                JPanel legendPanel = new JPanel();
                GridBagConstraints legendPanelC = createGridBagConstraints(0, 1, 2, 1);
                legendPanelC.fill = GridBagConstraints.BOTH;
                legendPanelC.weightx = 0.5;
                legendPanelC.weighty = 0.1;

                int[] j = {0};//Using an array means it doesn't have to be atomic for some reason
                ArrayList<Color> colourList = myPie.getColours();
                float total = myPie.getTotal();
                graphable.forEach((k, v) -> {
                    JLabel legendLabel = new JLabel(String.format("%s $%,.2f %.0f%% ", k, v, v/total*100f));
                    legendLabel.setForeground(colourList.get(j[0]));
                    legendPanel.add(legendLabel);
                    j[0]++;
                });
                piePane.add(legendPanel, legendPanelC);

                JButton closeButton = new JButton("Close");
                closeButton.addActionListener(lamb -> {
                        graphFrame.dispose();
                });
                GridBagConstraints closeButtonC = createGridBagConstraints(0, 1, 3, 1);
                closeButtonC.anchor = GridBagConstraints.SOUTHEAST;
                piePane.add(closeButton, closeButtonC);

                graphFrame.add(piePane);
                graphFrame.pack();
                graphFrame.setVisible(true);
            }
        });

        GridBagConstraints graphButtonC = createGridBagConstraints(1, 1, 1, 1);
        graphButtonC.anchor = GridBagConstraints.SOUTHEAST;
        graphPane.add(graphButton, graphButtonC);

        JLabel instructionsLabel = new JLabel("Use the left column to select the tag that is displayed on the graph. Use the other columns to filter out entries.");
        GridBagConstraints instructionsLabelC = createGridBagConstraints(0, 1, 1, 1);
        instructionsLabelC.anchor = GridBagConstraints.SOUTHWEST;
        instructionsLabelC.weightx = 0.5;
        graphPane.add(instructionsLabel, instructionsLabelC);

        //Rows of tags, colomuns of values
        int widestRow = Db.maxValsForTag();
        String[] tags = Db.getTags();
        for(int i = 0; i < tags.length; i++){
            JButton tagNameButton = new JButton(tags[i]);
            tagNameButton.addActionListener(e -> {
                Color currentColor = tagNameButton.getBackground();
                //Toggle off all other buttons
                for(int j = 0; j < tags.length; j++){
                    Component buttonToExamine = graphPaneTag.getComponent(j*(widestRow+1));
                    buttonToExamine.setBackground(UIManager.getColor("Button.background"));
                }
                //Set my background
                if(currentColor != Color.GREEN){
                    tagNameButton.setBackground(Color.GREEN);
                }
            });
            graphPaneTag.add(tagNameButton);

            String[] values = Db.getValuesForTag(tags[i]);
            int valuesInThisRow = values.length;
            for(int j = 0; j < widestRow; j++){
                if(j < valuesInThisRow){
                    JButton valueButton = new JButton(values[j]);
                    valueButton.addActionListener(e -> {
                        Color currentColor = valueButton.getBackground();
                        if(currentColor == Color.GREEN){
                            valueButton.setBackground(Color.RED);
                        }
                        else if(currentColor == Color.RED){
                            valueButton.setBackground(UIManager.getColor("Button.background"));
                        }
                        else{
                            valueButton.setBackground(Color.GREEN);
                        }
                    });
                    graphPaneTag.add(valueButton);
                }
                else{
                    //invisible, reserve space
                    JButton invisibleItem = new JButton("");
                    invisibleItem.setVisible(false);
                    graphPaneTag.add(invisibleItem);
                }
            }
        }
    };

    //Lists the current entries and provides buttons to remove or modify them
    private void drawViewPane(){
        viewPane.removeAll();
        JPanel viewTagPane = new JPanel(new GridBagLayout());
        int numRows = Db.getNumEntries();
        for(int i = 0; i < numRows; i++){
            Entry entryToModify = Db.getEntry(i);

            JButton deleteButton = new JButton("Remove");
            deleteButton.addActionListener(e -> {
                Db.removeEntry(entryToModify);
                dateLabel.setText(Db.datesToLabel());
                drawGraphPane();
                drawViewPane();
            });

            GridBagConstraints deleteButtonC = createGridBagConstraints(0, 1, i, 1);
            deleteButtonC.fill = GridBagConstraints.HORIZONTAL;
            deleteButtonC.weightx = 0.1;
            viewTagPane.add(deleteButton, deleteButtonC);

            JButton modifyButton = new JButton("Modify");
            modifyButton.addActionListener(new ActionListener(){
                @Override
                public void actionPerformed(ActionEvent e){
                    JFrame editFrame = new JFrame("Edit entry");
                    try{
                        editFrame.setIconImage(ImageIO.read(this.getClass().getResource("icon.png")));
                    } catch(Exception unused){}//Too bad, no icon

                    EditPanel localPane = new EditPanel(frame, entryToModify);

                    JButton saveButton = new JButton("Modify");
                    saveButton.addActionListener(new ActionListener(){
                        @Override
                        public void actionPerformed(ActionEvent e){
                            Entry created = localPane.tryConstruct(false);
                            if (created != null){
                                Db.removeEntry(entryToModify);
                                Db.createEntry(created);
                                editFrame.dispose();
                                dateLabel.setText(Db.datesToLabel());
                                drawViewPane();
                                drawGraphPane();
                            }
                        };
                    });
                    GridBagConstraints saveButtonC = createGridBagConstraints(1, 1, 8, 1);
                    saveButtonC.anchor = GridBagConstraints.SOUTHEAST;
                    localPane.add(saveButton, saveButtonC);

                    JButton cancelButton = new JButton("Cancel");
                    cancelButton.addActionListener(lamb -> {
                        editFrame.dispose();
                    });
                    GridBagConstraints cancelButtonC = createGridBagConstraints(0, 1, 8, 1);
                    cancelButtonC.weightx = 1;
                    cancelButtonC.anchor = GridBagConstraints.SOUTHEAST;
                    localPane.add(cancelButton, cancelButtonC);

                    editFrame.add(localPane);
                    editFrame.pack();
                    editFrame.setVisible(true);
                };
            });
            GridBagConstraints modifyButtonC = createGridBagConstraints(1, 1, i, 1);
            modifyButtonC.fill = GridBagConstraints.HORIZONTAL;
            modifyButtonC.weightx = 0.1;
            viewTagPane.add(modifyButton, modifyButtonC);

            JLabel descriptionLabel = new JLabel(Db.getEntry(i).displayLine());
            GridBagConstraints descriptionLabelC = createGridBagConstraints(2, 1, i, 1);
            descriptionLabelC.weightx = 0.9;
            descriptionLabelC.fill = GridBagConstraints.HORIZONTAL;
            descriptionLabelC.insets = new Insets(5, 5, 5, 5);
            viewTagPane.add(descriptionLabel, descriptionLabelC);
        }

        JScrollPane viewScrollPane = new JScrollPane(viewTagPane);
        GridBagConstraints viewScrollPaneC = createGridBagConstraints(0, 1, 0, 1);
        viewScrollPaneC.weightx = 0.5;
        viewScrollPaneC.weighty = 0.5;
        viewScrollPaneC.fill = GridBagConstraints.BOTH;
        viewPane.add(viewScrollPane, viewScrollPaneC);
    };

    //Only holds the API key at present
    private JPanel createCfgPane(){
        JPanel cfgPane = new JPanel(new GridBagLayout());

        //API key
        JLabel apiLabel = new JLabel("Alpha Vantage API Key: ");
        GridBagConstraints apiLabelC = createGridBagConstraints(0, 1, 0, 1);
        cfgPane.add(apiLabel, apiLabelC);

        JTextField apiField = new JTextField(18); //Key is 16 columns, use 18 for space
        apiField.setText(Db.getApiKey());
        apiField.addFocusListener(new FocusListener() {
            @Override
            public void focusGained(FocusEvent e) {} //Do nothing

            @Override
            public void focusLost(FocusEvent e) {
                Db.setApiKey(apiField.getText());
            }
        });

        GridBagConstraints apiFieldC = createGridBagConstraints(1, 1, 0, 1);
        cfgPane.add(apiField, apiFieldC);

        //Graph currency
        JLabel graphCurrLabel = new JLabel("Graph currency: ");
        GridBagConstraints graphCurrLabelC = createGridBagConstraints(0, 1, 1, 1);
        cfgPane.add(graphCurrLabel, graphCurrLabelC);

        JComboBox<Currency> graphCurrBox = new JComboBox<Currency>(Currency.values());
        graphCurrBox.setSelectedItem(Db.getGraphCurrency());
        GridBagConstraints graphCurrBoxC = createGridBagConstraints(1, 1, 1, 1);
        graphCurrBoxC.anchor = GridBagConstraints.WEST;
        cfgPane.add(graphCurrBox, graphCurrBoxC);

        //Exchange rate
        boolean willUpdateRate = Db.getAutoRate();
        JTextField rateField = new JTextField(willUpdateRate ? "" : Db.getExchangeRate(), 10); //Do not show outdated price
        rateField.setEditable(!willUpdateRate);
        rateField.addFocusListener(new FocusListener() {
            @Override
            public void focusGained(FocusEvent e) {} //Do nothing

            @Override
            public void focusLost(FocusEvent e) {
                if (Db.getAutoRate()) //Do nothing if not enabled
                    return;
                String rateText = rateField.getText();
                try{
                    float newRate = Float.parseFloat(rateText);
                    Db.setExchangeRate(newRate);
                } catch (NumberFormatException unused){
                    //Silently undo
                    rateField.setText(Db.getExchangeRate());
                }
            }
        });

        GridBagConstraints rateFieldC = createGridBagConstraints(1, 1, 2, 1);
        cfgPane.add(rateField, rateFieldC);

        JCheckBox autoRate = new JCheckBox("Automatic exchange rate, else specify CAD to USD:", Db.getAutoRate());
        autoRate.addItemListener(e -> {
            rateField.setEditable(Db.getAutoRate());
            Db.toggleAutoRate();
            if(e.getStateChange() == 1)
                rateField.setText(Db.getExchangeRate());
        });
        GridBagConstraints autoRateC = Main.createGridBagConstraints(0, 1, 2, 1);
        cfgPane.add(autoRate, autoRateC);

        return cfgPane;
    };

}
