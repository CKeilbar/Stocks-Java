package main;

import java.util.*;
import java.io.*;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.beans.*;
import javax.imageio.ImageIO;

public class Main {

    public static ArrayList<Entry> entries = new ArrayList<Entry>();
    private final String saveFname = "prevInfo.txt";
    private TagTracker tagMap = new TagTracker();

    //UI Stuff
    JFrame frame = new JFrame("Stock Visualizer");
    JPanel addPane = new JPanel(new GridBagLayout());
    JPanel editPane = new JPanel(new GridBagLayout());
    JPanel graphPane = new JPanel(new GridBagLayout());
    JTabbedPane mainPane = new JTabbedPane();

    public static void main(String[] args) {
        Main tempMain = new Main();
    }

    //Initializes the UI
    public Main(){
        readPrev();

        /*try{//Windows does not respect setting the button colour
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch(Exception e){}//Too bad, default look it is*/

        try{
            frame.setIconImage(ImageIO.read(this.getClass().getResource("icon.png")));
        } catch(Exception e){}//Too bad, no icon

        //Save before exit
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.addWindowListener(new WindowAdapter(){
            public void windowClosing(WindowEvent e){
                writePrev();
            };
        });

        //Set up UI
        drawAddPane();
        drawEditPane();
        drawGraphPane();

        mainPane.addTab("Add items", addPane);
        mainPane.addTab("Edit items", editPane);
        mainPane.addTab("Graph items", graphPane);

        frame.add(mainPane);
        frame.pack();//Automatically sets the size, probably not optimal...
        frame.setVisible(true);
    }

    //Reads from the local file for our saved entries
    private void readPrev(){
        try(BufferedReader br = new BufferedReader(new FileReader(saveFname))){
            String line;
            //Entries are in the following format:
            //name, quantity, updatePrice, price, [optional tag 1, optional value 1], ...
            while((line = br.readLine()) != null){
                String[] splitLine = line.split(",");
                Entry currentEntry = new Entry(splitLine[0], Integer.parseInt(splitLine[1]), "yes".equals(splitLine[2]), Float.parseFloat(splitLine[3]));
                for(int i = 4; i < splitLine.length; i += 2){
                    currentEntry.addValue(splitLine[i], splitLine[i+1]);
                    tagMap.addEntry(splitLine[i], splitLine[i+1]);

                }
                entries.add(currentEntry);
            }

        } catch (IOException e){
            //Very bad
        }
    };

    //Writes to the local file to store our entries for next time
    private void writePrev(){
        try(FileWriter fw = new FileWriter(saveFname)){
            for(Entry i : entries){
                fw.write(i.saveableLine());
            }
        } catch (IOException e){
            //Very bad
        }
    };

    //This function must deal with the tag manager and the list of tags
    private void removeEntry(Entry entry){
        entries.remove(entry);
        Set<Map.Entry<String, String>> tags = entry.getIterable();
        for(Map.Entry<String, String> i : tags){
            tagMap.removeEntry(i.getKey(), i.getValue());
        }
    }

    //Convenience method for creating the constraints
    //Aditional manual configuration will be optionally needed to set the weights
    private GridBagConstraints createGridBagConstraints(int xLoc, int xWidth, int yLoc, int yHeight){
        GridBagConstraints returnable = new GridBagConstraints();
        returnable.gridx = xLoc;
        returnable.gridwidth = xWidth;
        returnable.gridy = yLoc;
        returnable.gridheight = yHeight;
        return returnable;
    };

    //This panel is used to create new entries
    private void drawAddPane(){
        JLabel tickerLabel = new JLabel("Name/Ticker");
        GridBagConstraints tickerLabelC = createGridBagConstraints(0, 1, 0, 1);
        addPane.add(tickerLabel, tickerLabelC);

        JTextField tickerField = new JTextField("");
        GridBagConstraints tickerFieldC = createGridBagConstraints(1, 1, 0, 1);
        tickerFieldC.weightx = 0.5;
        tickerFieldC.fill = GridBagConstraints.HORIZONTAL;
        tickerFieldC.anchor = GridBagConstraints.EAST;
        addPane.add(tickerField, tickerFieldC);

        JLabel quantityLabel = new JLabel("Quantity");
        GridBagConstraints quantityLabelC = createGridBagConstraints(0, 1, 1, 1);
        addPane.add(quantityLabel, quantityLabelC);

        JTextField quantityField = new JTextField("");
        GridBagConstraints quantityFieldC = createGridBagConstraints(1, 1, 1, 1);
        quantityFieldC.weightx = 0.5;
        quantityFieldC.fill = GridBagConstraints.HORIZONTAL;
        quantityFieldC.anchor = GridBagConstraints.EAST;
        addPane.add(quantityField, quantityFieldC);

        JTextField priceField = new JTextField("");
        priceField.setEditable(false);
        GridBagConstraints priceFieldC = createGridBagConstraints(1, 1, 2, 1);
        priceFieldC.weightx = 0.5;
        priceFieldC.fill = GridBagConstraints.HORIZONTAL;
        priceFieldC.anchor = GridBagConstraints.EAST;
        addPane.add(priceField, priceFieldC);

        JCheckBox updateBox = new JCheckBox("Automatic price, else specify:", true);
        updateBox.addItemListener(e -> {
            if(e.getStateChange() == 1){
                priceField.setText("");
                priceField.setEditable(false);
            }
            else{
                priceField.setEditable(true);
            }
        });

        GridBagConstraints updateBoxC = createGridBagConstraints(0, 1, 2, 1);
        addPane.add(updateBox, updateBoxC);

        JPanel tagsPane = new JPanel(new GridLayout(0, 2));
        tagsPane.add(new JLabel("Tag"));
        tagsPane.add(new JLabel("Value"));

        ArrayList<JComboBox> boxes = new ArrayList();

        //This button adds a new row for a new tag and value
        JButton addItemButton = new JButton("+");
        addItemButton.addActionListener(e -> {
            tagsPane.remove(addItemButton);
            JComboBox<String> newTagField = new JComboBox<String>(tagMap.getTags());
            newTagField.setEditable(true);

            tagsPane.add(newTagField);
            boxes.add(newTagField);

            JComboBox<String> newTagValueField = new JComboBox<String>();
            newTagValueField.setEditable(true);
            Component component = newTagValueField.getEditor().getEditorComponent();
            if (component instanceof JTextField){
                JTextField comboField = (JTextField) component;
                comboField.addFocusListener(new FocusListener(){
                    @Override
                    public void focusGained(FocusEvent e){//Prepopulate with valid entry
                        Object tag = newTagField.getSelectedItem();
                        Object currentVal = newTagValueField.getSelectedItem();
                        if(tag != null && tagMap.getValuesForTag(tag.toString()) != null){
                            String[] validValues = tagMap.getValuesForTag(tag.toString());
                            newTagValueField.removeAllItems();
                            for(String entry : validValues){
                                newTagValueField.addItem(entry);
                            }
                            if(currentVal != null){
                                newTagValueField.setSelectedItem(currentVal);
                            }
                        }
                    };

                    @Override
                    public void focusLost(FocusEvent e){

                    };
                });
            }

            tagsPane.add(newTagValueField);
            boxes.add(newTagValueField);

            tagsPane.add(addItemButton);
            frame.getContentPane().validate();
            frame.getContentPane().repaint();
        });
        tagsPane.add(addItemButton);

        JScrollPane tagScrollPane = new JScrollPane(tagsPane);
        GridBagConstraints tagScrollPaneC = createGridBagConstraints(0, 2, 3, 4);
        tagScrollPaneC.weighty = 0.5;
        tagScrollPaneC.weightx = 0.5;
        tagScrollPaneC.fill = GridBagConstraints.BOTH;
        addPane.add(tagScrollPane, tagScrollPaneC);

        JButton saveButton = new JButton("Create");
        saveButton.addActionListener(e -> {
            //Create/allocate, warn if failure
            String msg = "The entry was created successfully.";
            boolean resultPassed = true;

            int quantity = 0;
            String quantityText = quantityField.getText();
            try{
                quantity = Integer.parseInt(quantityText);
            } catch(NumberFormatException ex){
                msg = "Could not interpret the quantity field (" + quantityText + ") as text.";
                resultPassed = false;
            }
            if(resultPassed){//No point doing price if quantity is wrong
                boolean willUpdatePrice = updateBox.isSelected();
                float price = 0f;
                String priceText = priceField.getText();
                String tickerText = tickerField.getText();
                if(!willUpdatePrice){
                    try{
                        price = Float.parseFloat(priceText);
                    } catch(NumberFormatException ex){
                        msg = "Could not interpret the price field (" + priceText + ") as text.";
                        resultPassed = false;
                    }
                }
                else{
                    price = PriceWorker.updatePrice(tickerText);
                    if(price == -1f){
                        msg = "Could not find a price for the ticker (" + tickerText + ").";
                        resultPassed = false;
                    }
                }

                if(resultPassed){//All checks complete
                    //Create the entry and clear the fields
                    Entry toAdd = new Entry(tickerText, quantity, willUpdatePrice, price);
                    for(int i = 0; i < boxes.size(); i += 2){
                        Object tagO = boxes.get(i).getSelectedItem();
                        if(tagO == null){
                            continue;
                        }
                        String tag = tagO.toString();

                        Object valueO = boxes.get(i + 1).getSelectedItem();
                        if(valueO == null){
                            continue;
                        }
                        String value = valueO.toString();
                        if(!"".equals(tag) && !"".equals(value)){
                            toAdd.addValue(tag, value);
                            tagMap.addEntry(tag, value);
                        }
                    }

                    entries.add(toAdd);

                    //Reset fields
                    quantityField.setText("");
                    tickerField.setText("");
                    priceField.setText("");
                    if(!willUpdatePrice){
                        updateBox.doClick();
                    }
                    for(Component i : tagsPane.getComponents()){
                        if(i instanceof JComboBox){
                            tagsPane.remove((JComboBox) i);
                        }
                    }
                    boxes.clear();
                    frame.getContentPane().validate();
                    frame.getContentPane().repaint();
                    //Both other panes need to be redrawn
                    drawGraphPane();
                    drawEditPane();
                }
            }


            JOptionPane.showMessageDialog(frame, msg, (resultPassed ? "Entry creation successful" : "Entry creation unsuccessful"), (resultPassed ? JOptionPane.INFORMATION_MESSAGE : JOptionPane.ERROR_MESSAGE));
        });
        GridBagConstraints saveButtonC = createGridBagConstraints(1, 1, 7, 1);
        saveButtonC.anchor = GridBagConstraints.SOUTHEAST;
        addPane.add(saveButton, saveButtonC);

        //Button unique to this tab
        JButton updateButton = new JButton("Update all prices");
        updateButton.addActionListener(e -> {
            //Blocks others
            JDialog progressDialog = new JDialog(frame, "Updating prices...", Dialog.ModalityType.APPLICATION_MODAL);

            JProgressBar progressBar = new JProgressBar(0, entries.size());
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
                    if(progress != entries.size()){
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
                        } catch(Exception ex){

                            JOptionPane.showMessageDialog(frame, "Reading progress returned an exception.", "Price update unknown", JOptionPane.ERROR_MESSAGE);
                        }

                        drawEditPane();
                        progressDialog.dispose();
                    }
                };
            });
            progressDialog.add(progressBar);
            priceWorker.execute();
            progressDialog.pack();
            progressDialog.setVisible(true);
        });
        GridBagConstraints updateButtonC = createGridBagConstraints(0, 1, 7, 1);
        updateButtonC.anchor = GridBagConstraints.SOUTHWEST;
        addPane.add(updateButton, updateButtonC);
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
        Map<String, Float> retMap = new LinkedHashMap();//Order is required
        for(Entry i : clone){
            String myTag = i.valueForTag(axis);
            retMap.put(myTag, i.getValue() + retMap.getOrDefault(myTag, 0f));
        }
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
            ArrayList<String> includeValues = new ArrayList();
            ArrayList<String> includeValueTags = new ArrayList();
            ArrayList<String> removeValues = new ArrayList();
            ArrayList<String> removeValueTags = new ArrayList();
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
            if("".equals(axisTag)){
                JOptionPane.showMessageDialog(frame, "No axis specified.", "Error", JOptionPane.ERROR_MESSAGE);
                return;
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
                } catch(Exception ex){}//Too bad, no icon

                graphFrame.setPreferredSize(new Dimension(600, 600));

                JPanel piePane = new JPanel(new GridBagLayout());

                JLabel graphTitle = new JLabel(axisTag);
                GridBagConstraints graphTitleC = createGridBagConstraints(0, 1, 0, 1);
                piePane.add(graphTitle, graphTitleC);

                Pie myPie = new Pie(graphable);
                GridBagConstraints myPieC = createGridBagConstraints(0, 1, 1, 1);
                myPieC.fill = GridBagConstraints.BOTH;
                myPieC.weightx = 0.5;
                myPieC.weighty = 0.9;
                piePane.add(myPie, myPieC);

                JPanel legendPanel = new JPanel();
                GridBagConstraints legendPanelC = createGridBagConstraints(0, 1, 2, 1);
                legendPanelC.fill = GridBagConstraints.BOTH;
                legendPanelC.weightx = 0.5;
                legendPanelC.weighty = 0.1;

                int[] j = {0};//Using an array means it doesn't have to be atomic for some reason
                ArrayList<Color> colourList = myPie.getColours();
                float total = myPie.getTotal();
                graphable.forEach((k, v) -> {
                    JLabel legendLabel = new JLabel(String.format("%s $%.2f %.0f%% ", k, v, v/total*100f));
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
                //Toggle off all other buttons
                for(int j = 0; j < tags.length; j++){
                    Component buttonToExamine = graphPaneTag.getComponent(j*(widestRow+1));
                    buttonToExamine.setBackground(UIManager.getColor("Button.background"));
                }
                //Set my background
                if(tagNameButton.getBackground() != Color.GREEN){
                    tagNameButton.setBackground(Color.GREEN);
                }
                else{
                    tagNameButton.setBackground(UIManager.getColor("Button.background"));
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

    //Basically the exact same as the edit pane, they should be merged in the future
    private void drawEditPane(){
        editPane.removeAll();
        JPanel editTagPane = new JPanel(new GridBagLayout());
        int numRows = entries.size();
        for(int i = 0; i < numRows; i++){
            Entry entryToModify = entries.get(i);

            JButton deleteButton = new JButton("Remove");
            deleteButton.addActionListener(e -> {
                removeEntry(entryToModify);
                drawGraphPane();
                drawEditPane();
            });

            GridBagConstraints deleteButtonC = createGridBagConstraints(0, 1, i, 1);
            deleteButtonC.fill = GridBagConstraints.HORIZONTAL;
            deleteButtonC.weightx = 0.1;
            editTagPane.add(deleteButton, deleteButtonC);

            JButton modifyButton = new JButton("Modify");
            modifyButton.addActionListener(new ActionListener(){
                @Override
                public void actionPerformed(ActionEvent e){
                    JFrame editFrame = new JFrame("Edit entry");
                    try{
                        editFrame.setIconImage(ImageIO.read(this.getClass().getResource("icon.png")));
                    } catch(Exception ex){}//Too bad, no icon

                    JPanel editPanel2 = new JPanel(new GridBagLayout());
                    JLabel tickerLabel = new JLabel("Name/Ticker");
                    GridBagConstraints tickerLabelC = createGridBagConstraints(0, 1, 0, 1);
                    editPanel2.add(tickerLabel, tickerLabelC);

                    JTextField tickerField = new JTextField(entryToModify.getTicker());
                    GridBagConstraints tickerFieldC = createGridBagConstraints(1, 1, 0, 1);
                    tickerFieldC.weightx = 0.5;
                    tickerFieldC.fill = GridBagConstraints.HORIZONTAL;
                    tickerFieldC.anchor = GridBagConstraints.EAST;
                    editPanel2.add(tickerField, tickerFieldC);

                    JLabel quantityLabel = new JLabel("Quantity");
                    GridBagConstraints quantityLabelC = createGridBagConstraints(0, 1, 1, 1);
                    editPanel2.add(quantityLabel, quantityLabelC);

                    JTextField quantityField = new JTextField(entryToModify.getQuantity());
                    GridBagConstraints quantityFieldC = createGridBagConstraints(1, 1, 1, 1);
                    quantityFieldC.weightx = 0.5;
                    quantityFieldC.fill = GridBagConstraints.HORIZONTAL;
                    quantityFieldC.anchor = GridBagConstraints.EAST;
                    editPanel2.add(quantityField, quantityFieldC);

                    JTextField priceField = new JTextField(entryToModify.getPrice());
                    priceField.setEditable(!entryToModify.getUpdatePrice());
                    GridBagConstraints priceFieldC = createGridBagConstraints(1, 1, 2, 1);
                    priceFieldC.weightx = 0.5;
                    priceFieldC.fill = GridBagConstraints.HORIZONTAL;
                    priceFieldC.anchor = GridBagConstraints.EAST;
                    editPanel2.add(priceField, priceFieldC);

                    JCheckBox updateBox = new JCheckBox("Automatic price, else specify:", entryToModify.getUpdatePrice());
                    updateBox.addItemListener(new ItemListener(){
                        @Override
                        public void itemStateChanged(ItemEvent e){
                            if(e.getStateChange() == 1){
                            priceField.setText("");
                            priceField.setEditable(false);
                            }
                            else{
                                priceField.setEditable(true);
                            }

                        };
                    });
                    GridBagConstraints updateBoxC = createGridBagConstraints(0, 1, 2, 1);
                    editPanel2.add(updateBox, updateBoxC);

                    JPanel tagsPane = new JPanel(new GridLayout(0, 2));
                    tagsPane.add(new JLabel("Tag"));
                    tagsPane.add(new JLabel("Value"));

                    ArrayList<JComboBox> boxes = new ArrayList();

                    JButton addItemButton = new JButton("+");
                    addItemButton.addActionListener(new ActionListener(){
                        @Override
                        public void actionPerformed(ActionEvent e){
                            tagsPane.remove(addItemButton);
                            JComboBox<String> newTagField = new JComboBox<String>(tagMap.getTags());
                            newTagField.setEditable(true);

                            tagsPane.add(newTagField);
                            boxes.add(newTagField);

                            JComboBox<String> newTagValueField = new JComboBox<String>();
                            newTagValueField.setEditable(true);
                            Component component = newTagValueField.getEditor().getEditorComponent();
                            if (component instanceof JTextField){
                                JTextField comboField = (JTextField) component;
                                comboField.addFocusListener(new FocusListener(){
                                    @Override
                                    public void focusGained(FocusEvent e){
                                        Object tag = newTagField.getSelectedItem();
                                        Object currentVal = newTagValueField.getSelectedItem();
                                        if(tag != null && tagMap.getValuesForTag(tag.toString()) != null){
                                            String[] validValues = tagMap.getValuesForTag(tag.toString());
                                            newTagValueField.removeAllItems();
                                            for(String entry : validValues){
                                                newTagValueField.addItem(entry);
                                            }
                                            if(currentVal != null){
                                                newTagValueField.setSelectedItem(currentVal);
                                            }
                                        }
                                    };

                                    @Override
                                    public void focusLost(FocusEvent e){

                                    };
                                });
                            }

                            tagsPane.add(newTagValueField);
                            boxes.add(newTagValueField);

                            tagsPane.add(addItemButton);
                            editFrame.getContentPane().validate();
                            editFrame.getContentPane().repaint();
                        };
                    });
                    tagsPane.add(addItemButton);

                    JScrollPane tagScrollPane = new JScrollPane(tagsPane);
                    GridBagConstraints tagScrollPaneC = createGridBagConstraints(0, 2, 3, 4);
                    tagScrollPaneC.weighty = 0.5;
                    tagScrollPaneC.weightx = 0.5;
                    tagScrollPaneC.fill = GridBagConstraints.BOTH;
                    editPanel2.add(tagScrollPane, tagScrollPaneC);

                    //Initialize the boxes
                    int i = 0;
                    Set<Map.Entry<String, String>> tags = entryToModify.getIterable();
                    for(Map.Entry<String, String> entry : tags){
                        addItemButton.doClick();
                        boxes.get(i).setSelectedItem(entry.getKey());
                        boxes.get(i+1).setSelectedItem(entry.getValue());
                        i += 2;
                    }

                    JButton saveButton = new JButton("Modify");
                    saveButton.addActionListener(new ActionListener(){
                        @Override
                        public void actionPerformed(ActionEvent e){
                            //Create/allocate, warn if failure
                            String msg = "The entry was modified successfully.";
                            boolean resultPassed = true;

                            int quantity = 0;
                            String quantityText = quantityField.getText();
                            try{
                                quantity = Integer.parseInt(quantityText);
                            } catch(NumberFormatException ex){
                                msg = "Could not interpret the quantity field (" + quantityText + ") as text.";
                                resultPassed = false;
                            }
                            boolean willUpdatePrice = updateBox.isSelected();
                            float price = 0f;
                            String priceText = priceField.getText();
                            String tickerText = tickerField.getText();
                            if(!willUpdatePrice){
                                try{
                                    price = Float.parseFloat(priceText);
                                } catch(NumberFormatException ex){
                                    msg = "Could not interpret the price field (" + priceText + ") as text.";
                                    resultPassed = false;
                                }
                            }
                            else{
                                price = PriceWorker.updatePrice(tickerText);
                                if(price == -1f){
                                    msg = "Could not find a price for the ticker (" + tickerText + ").";
                                    resultPassed = false;
                                }
                            }
                            if(resultPassed){
                                //Modify the entry
                                //First remove
                                removeEntry(entryToModify);

                                //Add the new
                                Entry toAdd = new Entry(tickerText, quantity, willUpdatePrice, price);
                                for(int i = 0; i < boxes.size(); i += 2){
                                    Object tagO = boxes.get(i).getSelectedItem();
                                    if(tagO == null){
                                        continue;
                                    }
                                    String tag = tagO.toString();

                                    Object valueO = boxes.get(i + 1).getSelectedItem();
                                    if(valueO == null){
                                        continue;
                                    }
                                    String value = valueO.toString();
                                    if(!"".equals(tag) && !"".equals(value)){
                                        toAdd.addValue(tag, value);
                                        tagMap.addEntry(tag, value);
                                    }
                                }

                                entries.add(toAdd);
                                editFrame.dispose();
                                drawEditPane();
                                drawGraphPane();
                            }

                            JOptionPane.showMessageDialog(frame, msg, (resultPassed ? "Entry modified successfully" : "Entry creation unsuccessful"), (resultPassed ? JOptionPane.INFORMATION_MESSAGE : JOptionPane.ERROR_MESSAGE));
                        };
                    });
                    GridBagConstraints saveButtonC = createGridBagConstraints(1, 1, 7, 1);
                    saveButtonC.anchor = GridBagConstraints.SOUTHEAST;
                    editPanel2.add(saveButton, saveButtonC);

                    JButton cancelButton = new JButton("Cancel");
                    cancelButton.addActionListener(lamb -> {
                        editFrame.dispose();
                    });
                    GridBagConstraints cancelButtonC = createGridBagConstraints(0, 1, 7, 1);
                    cancelButtonC.weightx = 1;
                    cancelButtonC.anchor = GridBagConstraints.SOUTHEAST;
                    editPanel2.add(cancelButton, cancelButtonC);

                    editFrame.add(editPanel2);
                    editFrame.pack();
                    editFrame.setVisible(true);
                };
            });
            GridBagConstraints modifyButtonC = createGridBagConstraints(1, 1, i, 1);
            modifyButtonC.fill = GridBagConstraints.HORIZONTAL;
            modifyButtonC.weightx = 0.1;
            editTagPane.add(modifyButton, modifyButtonC);

            JLabel descriptionLabel = new JLabel(entries.get(i).displayLine());
            GridBagConstraints descriptionLabelC = createGridBagConstraints(2, 1, i, 1);
            descriptionLabelC.weightx = 0.9;
            descriptionLabelC.fill = GridBagConstraints.HORIZONTAL;
            descriptionLabelC.insets = new Insets(5, 5, 5, 5);
            editTagPane.add(descriptionLabel, descriptionLabelC);
        }

        JScrollPane editScrollPane = new JScrollPane(editTagPane);
        GridBagConstraints editScrollPaneC = createGridBagConstraints(0, 1, 0, 1);
        editScrollPaneC.weightx = 0.5;
        editScrollPaneC.weighty = 0.5;
        editScrollPaneC.fill = GridBagConstraints.BOTH;
        editPane.add(editScrollPane, editScrollPaneC);

    };
}

