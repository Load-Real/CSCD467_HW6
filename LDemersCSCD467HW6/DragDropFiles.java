//Names: Louden Demers, Samuel Shaw, Kevin Nguyen
//Current Issue: You cannot download a file you uploaded during this current run, BUT you can restart the program
//and download that file in the next run. Upload always works, download works on previously uploaded files, and both these
//functionalities will continue to work even after an error has appeared.

//import javax.swing.event.*;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.BufferedReader;
//import java.awt.event.*;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
//import java.io.OutputStreamWriter;
import java.io.PrintWriter;
//import java.io.Writer;
//import java.util.UUID;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListModel;
import javax.swing.DropMode;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTree;
import javax.swing.SwingUtilities;
import javax.swing.TransferHandler;
import javax.swing.UIManager;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;

//import com.amazonaws.auth.AWSCredentials;
//import com.amazonaws.auth.AWSStaticCredentialsProvider;
//import com.amazonaws.auth.profile.ProfileCredentialsProvider;
import com.amazonaws.regions.Regions;
//import com.amazonaws.services.mediastoredata.model.GetObjectRequest;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.Bucket;
//import com.amazonaws.services.s3.model.ListObjectsRequest;
import com.amazonaws.services.s3.model.ObjectListing;
import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.services.s3.model.S3ObjectSummary;

public class DragDropFiles extends JFrame {

	private DefaultListModel model = new DefaultListModel();
	private int count = 0;
	private JTree tree;
	private JLabel label;
	private JButton download;
	private DefaultTreeModel treeModel;
	private TreePath namesPath;
	private JPanel wrap;
	private TreePath downloadPath = null;
	// private static AWSCredentials credentials = new
	// ProfileCredentialsProvider("default").getCredentials();
	private static AmazonS3 s3 = AmazonS3ClientBuilder.standard().withRegion(Regions.US_WEST_2).build();

	private static DefaultTreeModel getDefaultTreeModel() {
		DefaultMutableTreeNode root = new DefaultMutableTreeNode("All My Buckets");
		DefaultMutableTreeNode parent;

		// Create an EC3 instance using default AWS credentials

		// AmazonS3 s3 = AmazonS3ClientBuilder.standard().withCredentials(new
		// AWSStaticCredentialsProvider(credentials))
		// .withRegion("us-west-2").build();

		// Add everything to the default structure
		for (Bucket b : s3.listBuckets()) {
			parent = new DefaultMutableTreeNode(b.getName());
			root.add(parent);

			ObjectListing objectListing = s3.listObjects(b.getName());
			for (S3ObjectSummary objectSummary : objectListing.getObjectSummaries()) {
				parent.add(new DefaultMutableTreeNode(objectSummary.getKey()));
			}
		}

		return new DefaultTreeModel(root);
	}

	public DragDropFiles() {
		super("Drag and Drop File Transfers in Cloud");

		treeModel = getDefaultTreeModel();

		tree = new JTree(treeModel);
		tree.setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4));
		tree.getSelectionModel().setSelectionMode(TreeSelectionModel.DISCONTIGUOUS_TREE_SELECTION);
		tree.setDropMode(DropMode.ON);
		tree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
		namesPath = tree.getPathForRow(2);
		tree.expandRow(2);
		tree.expandRow(1);
		tree.setRowHeight(0);

		// Handles the tree node selection event that triggered by user selection
		// Identify which tree node(file name) has been selected, for downloading.
		// For more info, see TreeSelectionListener Class in Java
		tree.addTreeSelectionListener(new TreeSelectionListener() {
			public void valueChanged(TreeSelectionEvent e) {
				// DefaultMutableTreeNode node = (DefaultMutableTreeNode)
				// tree.getLastSelectedPathComponent();

				/* if nothing is selected */
				// if (node == null) return;

				/* retrieve the node that was selected */
				// Object nodeInfo = node.getUserObject();
				// System.out.println("Node selected is:" + nodeInfo.toString());
				/* React to the node selection. */
				downloadPath = e.getNewLeadSelectionPath();
			}
		});

		tree.setTransferHandler(new TransferHandler() {

			public boolean canImport(TransferHandler.TransferSupport info) {
				// we'll only support drops (not clip-board paste)
				if (!info.isDrop()) {
					return false;
				}
				info.setDropAction(COPY); // Tony added
				info.setShowDropLocation(true);
				// we import Strings and files
				if (!info.isDataFlavorSupported(DataFlavor.stringFlavor)
						&& !info.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
					return false;
				}

				// fetch the drop location
				JTree.DropLocation dl = (JTree.DropLocation) info.getDropLocation();
				TreePath path = dl.getPath();

				// we don't support invalid paths or descendants of the names folder
				if (path == null) {
					return false;
				}
				return true;
			}

			public boolean importData(TransferHandler.TransferSupport info) {
				// if we can't handle the import, say so
				if (!canImport(info)) {
					return false;
				}
				// fetch the drop location
				JTree.DropLocation dl = (JTree.DropLocation) info.getDropLocation();

				// fetch the path and child index from the drop location
				TreePath path = dl.getPath();
				int childIndex = dl.getChildIndex();

				// fetch the data and bail if this fails
				String uploadName = "";

				Transferable t = info.getTransferable();
				try {
					java.util.List<File> l = (java.util.List<File>) t.getTransferData(DataFlavor.javaFileListFlavor);

					for (File f : l) {
						uploadName = f.getName();
						String copyName = "./copy-" + f.getName();
						File destFile = new File(copyName);
						copyFile(f, destFile);
						s3.putObject(path.getLastPathComponent().toString(), destFile.getName(), destFile);
						destFile.delete();
						break;// We process only one dropped file.
					}
				} catch (UnsupportedFlavorException e) {
					return false;
				} catch (IOException e) {
					return false;
				}

				// if child index is -1, the drop was on top of the path, so we'll
				// treat it as inserting at the end of that path's list of children
				if (childIndex == -1) {
					childIndex = tree.getModel().getChildCount(path.getLastPathComponent());
				}

				// create a new node to represent the data and insert it into the model
				DefaultMutableTreeNode newNode = new DefaultMutableTreeNode(uploadName);
				DefaultMutableTreeNode parentNode = (DefaultMutableTreeNode) path.getLastPathComponent();
				treeModel.insertNodeInto(newNode, parentNode, childIndex);

				// make the new node visible and scroll so that it's visible
				tree.makeVisible(path.pathByAddingChild(newNode));
				tree.scrollRectToVisible(tree.getPathBounds(path.pathByAddingChild(newNode)));

				// Display uploading status
				label.setText("Uploaded **" + uploadName + "** successfully!");
				
				return true;
			}

		});

		JPanel p = new JPanel();
		p.setLayout(new BoxLayout(p, BoxLayout.X_AXIS));
		this.wrap = new JPanel();
		this.label = new JLabel("Status Bar...");
		wrap.add(this.label);
		p.add(Box.createHorizontalStrut(4));
		p.add(Box.createGlue());
		p.add(wrap);
		p.add(Box.createGlue());
		p.add(Box.createHorizontalStrut(4));
		getContentPane().add(p, BorderLayout.NORTH);

		getContentPane().add(new JScrollPane(tree), BorderLayout.CENTER);
		download = new JButton("Download");
		download.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				// You have to program here in this method in response to downloading a file
				// from the cloud,
				// Refer to TreePath class about how to extract the bucket name and file name
				// out of
				// the downloadPath object.
				if (downloadPath != null) {
					JOptionPane.showMessageDialog(null,
							"You like to downloand a file from cloud from buckets:" + downloadPath.toString());

					S3Object object = s3.getObject(downloadPath.getParentPath().getLastPathComponent().toString(),
							downloadPath.getLastPathComponent().toString());
					
					try {
						PrintWriter file = new PrintWriter(new File(object.getKey()));
						BufferedReader input = new BufferedReader(new InputStreamReader(object.getObjectContent()));
						String line = input.readLine();

						while (line != null) {
							file.println(line);
							line = input.readLine();
						}
						object.close();
						file.close();
						input.close();

					} catch (Exception e1) {
						e1.printStackTrace();
					}
				}
			}
		});
		
		//DefaultTreeModel treeModel;

		p = new JPanel();
		p.setLayout(new BoxLayout(p, BoxLayout.X_AXIS));
		wrap = new JPanel();
		// wrap.add(new JLabel("Show drop location:"));
		wrap.add(download);
		p.add(Box.createHorizontalStrut(4));
		p.add(Box.createGlue());
		p.add(wrap);
		p.add(Box.createGlue());
		p.add(Box.createHorizontalStrut(4));
		getContentPane().add(p, BorderLayout.SOUTH);

		getContentPane().setPreferredSize(new Dimension(400, 450));
	}

	private static void increaseFont(String type) {
		Font font = UIManager.getFont(type);
		font = font.deriveFont(font.getSize() + 4f);
		UIManager.put(type, font);
	}

	private static void createAndShowGUI() {
		// Create and set up the window.
		DragDropFiles test = new DragDropFiles();
		test.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

		// Display the window.
		test.pack();
		test.setVisible(true);
	}

	private void copyFile(File source, File dest) throws IOException {
		InputStream input = null;
		OutputStream output = null;
		try {
			input = new FileInputStream(source);
			output = new FileOutputStream(dest);
			byte[] buf = new byte[1024];
			int bytesRead;
			while ((bytesRead = input.read(buf)) > 0) {
				output.write(buf, 0, bytesRead);
			}
		} finally {
			input.close();
			output.close();
		}

	}

	public static void main(String[] args) {
		//Make some buckets for testing:
//		for (int i = 0; i < 3; i++) {
//			String bucketName = "s3-testbucket-" + UUID.randomUUID();
//			s3.createBucket(bucketName);
//		}
		
		SwingUtilities.invokeLater(new Runnable() {
			public void run() {
				try {
					UIManager.setLookAndFeel("com.sun.java.swing.plaf.windows.WindowsLookAndFeel");
					increaseFont("Tree.font");
					increaseFont("Label.font");
					increaseFont("ComboBox.font");
					increaseFont("List.font");
				} catch (Exception e) {
				}

				// Turn off metal's use of bold fonts
				UIManager.put("swing.boldMetal", Boolean.FALSE);
				createAndShowGUI();
			}
		});
	}
}