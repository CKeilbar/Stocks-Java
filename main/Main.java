package main;

import java.util.*;
import java.io.*;
import java.time.*;
import java.time.format.*;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.beans.*;
import javax.imageio.ImageIO;

public class Main {

    //Save file
    private final String saveFname = "prevInfo.txt";

    //Database information
    private ArrayList<Entry> entries = new ArrayList<Entry>();
    private TagTracker tagMap = new TagTracker();

    //Shared variables
    private LocalDateTime priceTime = LocalDateTime.now(); //Default to current, overwritten if previous is found
    private LocalDateTime dbTime = priceTime;
    private String prevApiKey = "";
    private String prevCurr = "CAD";

    //UI Stuff
    private JLabel dateLabel;
    private JFrame frame = new JFrame("Stock Visualizer");
    private JPanel viewPane = new JPanel(new GridBagLayout());
    private JPanel graphPane = new JPanel(new GridBagLayout());
    private JTextField apiField = new JTextField(18); //Key is 16 columns, use 18 for space
    private JComboBox<String> graphCurrBox;
    private JCheckBox autoRate;
    private JTextField rateField;

    public static void main(String[] args) {
        new Main();
    }

    //Initializes the UI
    public Main(){
        readPrev();

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
                writePrev();
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

    //Reads from the local file for our saved entries
    private void readPrev(){
        try(BufferedReader br = new BufferedReader(new FileReader(saveFname))){
            String line;

            //First line contains the config info in order
            line = br.readLine();
            String[] splitLine = line.split(",");
            prevApiKey = splitLine[0];
            prevCurr = splitLine[1];
            PriceWorker.shouldUpdate = "yes".equals(splitLine[2]);
            PriceWorker.setExchangeRate(Float.parseFloat(splitLine[3]));

            //Next two lines are the last price modification and database modification dates
            try{
                line = br.readLine();
                priceTime = LocalDateTime.parse(line);
                line = br.readLine();
                dbTime = LocalDateTime.parse(line);
            } catch (DateTimeParseException unused){
                //Use initialized defaults
            }
            //Rest of the lines correspond to entries
            while((line = br.readLine()) != null){
                Entry currentEntry = Entry.fromString(line);
                if (currentEntry != null){
                    createEntry(currentEntry);
                }
            }

        } catch (Exception unused){
            //Very bad
        }
    };

    //Writes to the local file to store our entries for next time
    private void writePrev(){
        try(BufferedWriter bw = new BufferedWriter(new FileWriter(saveFname))){

            bw.write(String.join(",", apiField.getText(), graphCurrBox.getSelectedItem().toString(), (PriceWorker.shouldUpdate ? "yes" : "no"), PriceWorker.getExchangeRate()));
            bw.newLine();
            bw.write(priceTime.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            bw.newLine();
            bw.write(dbTime.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            bw.newLine();
            for(Entry i : entries){
                bw.write(i.toString());
            }
        } catch (Exception unused){
            //Very bad
        }
    };

    //Returns the timestamps formatted as text
    private String datesToLabel(){
        DateTimeFormatter dispTimeFormat = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT);
        String startString = "Database last modified ";
        String dbString = "";
        String middleString = "; prices last updated ";
        String priceString = "";

        try {
            dbString = dbTime.format(dispTimeFormat);
            priceString = priceTime.format(dispTimeFormat);
        } catch (DateTimeException unused){
            //Use the default empty strings
        }
        return startString + dbString + middleString + priceString;
    }

    //These functions deal with the tag manager as well as the list
    private void removeEntry(Entry entry){
        entries.remove(entry);
        tagMap.removeEntry(entry.getIterable());
    }
    private void createEntry(Entry entry){
        entries.add(entry);
        tagMap.addEntry(entry.getIterable());
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

        EditPanel createPane = new EditPanel(frame, tagMap);

        dateLabel = new JLabel(datesToLabel());
        GridBagConstraints dateLabelC = createGridBagConstraints(0, 1, 8, 1);
        createPane.add(dateLabel, dateLabelC);

        JButton saveButton = new JButton("Create");
        saveButton.addActionListener(e -> {
            Entry created = createPane.tryConstruct(apiField.getText(), false);
            if (created != null){
                createEntry(created);

                //Update UI
                dbTime = LocalDateTime.now();
                dateLabel.setText(datesToLabel());
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

            JProgressBar progressBar = new JProgressBar(0, Math.max(entries.size(), 1));
            progressBar.setValue(0);
            progressBar.setString("Initializing");
            progressBar.setStringPainted(true);

            PriceWorker priceWorker = new PriceWorker(apiField.getText(), entries);
            priceWorker.addPropertyChangeListener(new PropertyChangeListener(){
                public void propertyChange(PropertyChangeEvent evt){
                    if(evt.getPropertyName() != "progress"){
                        return;
                    }
                    int progress = (Integer) evt.getNewValue();
                    progressBar.setValue(progress);
                    if(progress < entries.size()){
                        progressBar.setString(entries.get(progress).getTicker());
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

                        priceTime = LocalDateTime.now();
                        dateLabel.setText(datesToLabel());
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

    //Searches through the entries to find the ones satisfying the criteria
    //The inc values MUST be present, and the rem values MUST NOT, this functions as a logical AND
    private Map<String, Float> findGraphables(String axis, ArrayList<String> incVal, ArrayList<String> incTag, ArrayList<String> remVal, ArrayList<String> remTag){
        ArrayList<Entry> clone = new ArrayList<>(entries);

        for(int i = 0; i < incVal.size(); i++){//Remove all of the entries that don't satisfy the includes
            for(int j = clone.size()-1; j >= 0; j--){
                if(!clone.get(j).containsPair(incTag.get(i), incVal.get(i))){
                    clone.remove(j);
                }
            }
            if(clone.size() == 0){
                return null;
            }
        }
        for(int i = 0; i < remVal.size(); i++){//Remove all entries that satisfy the removes
            for(int j = clone.size()-1; j >= 0; j--){
                if(clone.get(j).containsPair(remTag.get(i), remVal.get(i))){
                    clone.remove(j);
                }
            }
            if(clone.size() == 0){
                return null;
            }
        }
        //Clone now holds only valid values
        Map<String, Float> unsortedMap = new HashMap<>();
        for(Entry i : clone){
            String myTag = "".equals(axis) ? i.getTicker() : i.valueForTag(axis); //If no axis, use ticker as label
            unsortedMap.put(myTag, i.getValue(graphCurrBox.getSelectedIndex() == 1, apiField.getText()) + unsortedMap.getOrDefault(myTag, 0f));
        }

        Map<String, Float> retMap = new LinkedHashMap<>();//Order is required
        //Sort by value
        unsortedMap.entrySet().stream().sorted(Collections.reverseOrder(Map.Entry.comparingByValue())).forEach(entry -> retMap.put(entry.getKey(), entry.getValue()));
        return retMap;
    }

    //Draws the pane used for graphing in addition to the one that holds the graph
    private void drawGraphPane(){
        graphPane.removeAll();

        JPanel graphPaneTag = new JPanel(new GridLayout(0, tagMap.maxValsForTag() + 1));

        JScrollPane graphScrollPane = new JScrollPane(graphPaneTag);
        GridBagConstraints graphScrollPaneC = createGridBagConstraints(0, 2, 0, 1);
        graphScrollPaneC.weightx = 0.5;
        graphScrollPaneC.weighty = 0.5;
        graphScrollPaneC.fill = GridBagConstraints.BOTH;
        graphPane.add(graphScrollPane, graphScrollPaneC);

        JButton graphButton = new JButton("Graph");

        //Launch the graph
        graphButton.addActionListener(e -> {
            int length = tagMap.maxValsForTag()+1;
            ArrayList<String> includeValues = new ArrayList<>();
            ArrayList<String> includeValueTags = new ArrayList<>();
            ArrayList<String> removeValues = new ArrayList<>();
            ArrayList<String> removeValueTags = new ArrayList<>();
            String axisTag = "";

            //Look at the buttons to see what has been selected
            for(int i = 0; i < tagMap.getTags().length * length; i++){
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
            Map<String, Float> graphable = findGraphables(axisTag, includeValues, includeValueTags, removeValues, removeValueTags);
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

                JLabel graphTitle = new JLabel(String.format("Category: %s - Total: $%,.2f %s", "".equals(axisTag) ? "None" : axisTag, myPie.getTotal(), graphCurrBox.getSelectedItem().toString()));
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
        int widestRow = tagMap.maxValsForTag();
        String[] tags = tagMap.getTags();
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

            String[] values = tagMap.getValuesForTag(tags[i]);
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
        int numRows = entries.size();
        for(int i = 0; i < numRows; i++){
            Entry entryToModify = entries.get(i);

            JButton deleteButton = new JButton("Remove");
            deleteButton.addActionListener(e -> {
                removeEntry(entryToModify);
                dbTime = LocalDateTime.now();
                dateLabel.setText(datesToLabel());
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

                    EditPanel localPane = new EditPanel(frame, tagMap, entryToModify);

                    JButton saveButton = new JButton("Modify");
                    saveButton.addActionListener(new ActionListener(){
                        @Override
                        public void actionPerformed(ActionEvent e){
                            Entry created = localPane.tryConstruct(apiField.getText(), false);
                            if (created != null){
                                removeEntry(entryToModify);
                                createEntry(created);
                                editFrame.dispose();
                                dbTime = LocalDateTime.now();
                                dateLabel.setText(datesToLabel());
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

            JLabel descriptionLabel = new JLabel(entries.get(i).displayLine());
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

        apiField.setText(prevApiKey);
        GridBagConstraints apiFieldC = createGridBagConstraints(1, 1, 0, 1);
        cfgPane.add(apiField, apiFieldC);

        //Graph currency
        JLabel graphCurrLabel = new JLabel("Graph currency: ");
        GridBagConstraints graphCurrLabelC = createGridBagConstraints(0, 1, 1, 1);
        cfgPane.add(graphCurrLabel, graphCurrLabelC);

        String[] opts = {"CAD", "USD"};
        graphCurrBox = new JComboBox<String>(opts);
        graphCurrBox.setSelectedItem(prevCurr);
        GridBagConstraints graphCurrBoxC = createGridBagConstraints(1, 1, 1, 1);
        graphCurrBoxC.anchor = GridBagConstraints.WEST;
        cfgPane.add(graphCurrBox, graphCurrBoxC);

        //Exchange rate
        rateField = new JTextField(PriceWorker.shouldUpdate ? "" : PriceWorker.getExchangeRate(), 10); //Do not show outdated price
        rateField.setEditable(!PriceWorker.shouldUpdate);
        rateField.addFocusListener(new FocusListener() {
            @Override
            public void focusGained(FocusEvent e) {} //Do nothing

            @Override
            public void focusLost(FocusEvent e) {
                String rateText = rateField.getText();
                try{
                    float newRate = Float.parseFloat(rateText);
                    PriceWorker.setExchangeRate(newRate);
                } catch (NumberFormatException unused){
                    //Silently undo
                    rateField.setText(PriceWorker.getExchangeRate());
                }
            }
        });

        GridBagConstraints rateFieldC = createGridBagConstraints(1, 1, 2, 1);
        cfgPane.add(rateField, rateFieldC);

        autoRate = new JCheckBox("Automatic exchange rate, else specify CAD to USD:", PriceWorker.shouldUpdate);
        autoRate.addItemListener(e -> {
            if(e.getStateChange() == 1){
                PriceWorker.shouldUpdate = true;
                PriceWorker.updateRate(apiField.getText());
                rateField.setText(PriceWorker.getExchangeRate());
                rateField.setEditable(false);
            }
            else{
                PriceWorker.shouldUpdate = false;
                rateField.setEditable(true);
            }
        });
        GridBagConstraints autoRateC = Main.createGridBagConstraints(0, 1, 2, 1);
        cfgPane.add(autoRate, autoRateC);

        return cfgPane;
    };

}
