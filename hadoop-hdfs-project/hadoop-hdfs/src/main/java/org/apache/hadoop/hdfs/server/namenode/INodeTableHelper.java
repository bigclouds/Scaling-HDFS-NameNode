package org.apache.hadoop.hdfs.server.namenode;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.hadoop.fs.permission.PermissionStatus;
import org.apache.hadoop.hdfs.server.blockmanagement.BlockInfo;
import org.apache.hadoop.io.DataInputBuffer;
import org.apache.hadoop.io.DataOutputBuffer;

import se.sics.clusterj.InodeTable;

import com.mysql.clusterj.ClusterJDatastoreException;
import com.mysql.clusterj.Query;
import com.mysql.clusterj.Session;

import com.mysql.clusterj.Transaction;
import com.mysql.clusterj.query.QueryBuilder;
import com.mysql.clusterj.query.QueryDomainType;

public class INodeTableHelper {
	public static Session session= DBConnector.sessionFactory.getSession() ;
	static final int MAX_DATA = 128;
	public static FSNamesystem ns = null;

	
	/*AddChild should take care of the different InodeOperations
	 * InodeDirectory, InodeDirectoryWithQuota, etc.
	 * TODO: InodeSymLink
	 */
	public static void addChild(INode node){
		boolean entry_exists;
		Transaction tx = session.currentTransaction();
	    tx.begin();
	    
	    QueryBuilder builder = session.getQueryBuilder();
	    QueryDomainType<InodeTable> domain =
	    builder.createQueryDefinition(InodeTable.class);
	    
	    domain.where(domain.get("name").equal(domain.param(
	    "name")));
	    Query<InodeTable> query = session.createQuery(domain);
	    query.setParameter("name",node.getFullPathName());
	    
	    List<InodeTable> results = query.getResultList();
	    System.err.println("[KTHFS] Try to reach DB with "+node.getFullPathName() + "is it empty" + results.isEmpty());
	    InodeTable inode;
	    //InodeTable inode = session.find(InodeTable.class, node.getFullPathName());
	    entry_exists = true;
	    if (results.isEmpty())
	    {
	    	inode = session.newInstance(InodeTable.class);
	    	inode.setId(System.currentTimeMillis());
	        System.err.println("[KTHFS] Fullpath for new row" + node.getFullPathName());
	        inode.setName(node.getFullPathName());
	        entry_exists = false;
	    }
	    else{
	    	inode = results.get(0);
	    }
	    inode.setModificationTime(node.modificationTime);
	    inode.setATime(node.getAccessTime());
	    inode.setLocalName(node.getLocalName());
	    DataOutputBuffer permissionString = new DataOutputBuffer();
	    try {
			node.getPermissionStatus().write(permissionString);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	    

	    
	    /* Commented by W
	    long finalPerm = 0;
	    try {
			permissionString.writeLong(finalPerm);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}	
	    */
	    
	    inode.setPermission(permissionString.getData());
	    
	    // Corner case for rootDir
	    if (node.getParent() != null)
	    	inode.setParent(node.getParent().getFullPathName());
	    
	    inode.setNSQuota(node.getNsQuota());
		inode.setDSQuota(node.getDsQuota());
	    if (node instanceof INodeDirectory)
	    {
	    	inode.setIsClosedFile(false);
	    	inode.setIsUnderConstruction(false);
	    	inode.setIsDirWithQuota(false);    
	    	inode.setIsDir(true);
	    }
	    if (node instanceof INodeDirectoryWithQuota)
	    {
	    	inode.setIsClosedFile(false);
	    	inode.setIsDir(false);	    	
	    	inode.setIsUnderConstruction(false);
	    	inode.setIsDirWithQuota(true);    	
	    	inode.setNSCount(((INodeDirectoryWithQuota) node).getNsCount());
	    	inode.setDSCount(((INodeDirectoryWithQuota) node).getDsCount());
	    }
	    if (node instanceof INodeFile)
	    {
	    	inode.setIsDir(false);
	    	inode.setIsUnderConstruction(false);
	    	inode.setIsDirWithQuota(false);
	    	inode.setIsClosedFile(true);
	    	inode.setHeader(((INodeFile) node).getHeader());
	    }
	    if (node instanceof INodeFileUnderConstruction)
	    {
	    	inode.setIsClosedFile(false);
	    	inode.setIsDir(false);
	    	inode.setIsDirWithQuota(false);
	    	inode.setIsUnderConstruction(true);	    	
	    	inode.setClientName(((INodeFileUnderConstruction) node).getClientName());
	    	inode.setClientMachine(((INodeFileUnderConstruction) node).getClientMachine());
	    	System.err.println("[STATELESS] Client name : " +((INodeFileUnderConstruction) node).getClientNode().getName());
	    	inode.setClientNode(((INodeFileUnderConstruction) node).getClientNode().getName());
	    }
	    if (node instanceof INodeSymlink)
	       	inode.setSymlink(((INodeSymlink) node).getSymlink());
	    
	    if (entry_exists){
	    	session.updatePersistent(inode);
	    }
	    else
	    {
	    	System.err.println("[KTHFS] Entry is new: "+ inode.getName());
	    	session.makePersistent(inode);
	    }
	    tx.commit();
	    session.flush();
	}
	
	
	public static List<INode> getChildren(String parentDir) throws IOException {

		QueryBuilder qb = session.getQueryBuilder();
		QueryDomainType<InodeTable> dobj = qb.createQueryDefinition(InodeTable.class);


		dobj.where(dobj.get("parent").equal(dobj.param("parent")));

		Query<InodeTable> query = session.createQuery(dobj);
		query.setParameter("parent", parentDir); //W: WHERE parent = parentDir

		List<InodeTable> resultList = query.getResultList();

		//List<String> children = new ArrayList<String>();
		List<INode> children = new ArrayList<INode>();

		for (InodeTable result : resultList) {
		
//				//create a directory object
//				DataInputBuffer buffer = new DataInputBuffer();
//				buffer.reset(result.getPermission(), result.getPermission().length);
//				PermissionStatus ps = PermissionStatus.read(buffer);
//				//KthFsHelper.printKTH("PermissionStatus: "+ps.getGroupName() + ps.getUserName() + " " + ps.getPermission().toString());
//				
///*
//				INode node = INode.newINode(
//						ps,//this.getPermissionStatus(),
//						null, //TODO: W: blocks to be read from DB also - null for directories
//						"", //symlink name
//						(short)1, //replication factor
//						result.getModificationTime(), 
//						result.getATime(),
//						result.getNSQuota(),
//						result.getDSQuota(),
//						-1);
//				node.setLocalName(result.getLocalName());
//				children.add(node);*/
//				
//				
//				if(result.getIsDir()) {
//                    INodeDirectory dir =  new INodeDirectory(result.getName(), ps);
//                    dir.setLocalName(result.getLocalName());
//                    children.add(dir);
//				}
//				else {
//                    INodeFile inf = new INodeFile(ps,0,(short)1,result.getModificationTime(), result.getATime(), 64); //FIXME: change this when we store blockinfo
//                    inf.setLocalName(result.getName());
//                    children.add(inf);
//                }
			
			INode inode = getINodeByNameBasic (result.getName ());

			//System.err.println("[STATELESS] retrieving parent " + result.getParent() + " " + result.getName());
			// Attach a parent to the Inode we just retrieved
			//INodeDirectory inodeParent = (INodeDirectory) getINodeByNameBasic(result.getParent());
			//System.err.println("[STATELESS] NAME IS: " + inode.getFullPathName());
			//inode.setParent(inodeParent);
			children.add(inode);
			
		}

		if (children.size() > 0 )
			return children;
		else 
			return null;


	}
	
	public static INode removeChild(INode node) throws ClusterJDatastoreException {
		System.err.println("[Stateless] Inode to be deleted: " + node.getFullPathName());
		Transaction tx = session.currentTransaction();
        tx.begin();
        QueryBuilder builder = session.getQueryBuilder();
	    QueryDomainType<InodeTable> domain =
	    builder.createQueryDefinition(InodeTable.class);
	    
	    domain.where(domain.get("name").equal(domain.param(
	    "name")));
	    Query<InodeTable> query = session.createQuery(domain);
	    query.setParameter("name",node.getFullPathName());
	    
	    List<InodeTable> results = query.getResultList();
	    
        //InodeTable inode = session.find(InodeTable.class, node.getFullPathName());
        session.deletePersistent(results.get(0));
        tx.commit();
        session.flush();
        
		return node;
	}
	
	/*
	 * This method is to be used in case an INodeDirectory is
	 * renamed (typically from the mv operation). When dealing
	 * with the DB, such an operation would isolate the sub-tree
	 * from the parent that was moved, and thus all nodes in the
	 * sub-tree need to have their fullpath and parent fields updated
	 *  
	 */
	public static void updateParentAcrossSubTree (String oldFullPathOfParent, String newFullPathOfParent) {
		
		QueryBuilder qb = session.getQueryBuilder();
		QueryDomainType<InodeTable> dobj = qb.createQueryDefinition(InodeTable.class);

		// FIXME: DB will be indexed by parent in the future.
		dobj.where(dobj.get("name").like(dobj.param("name_param")));

		Query<InodeTable> query = session.createQuery(dobj);
		
		// If fullPathOfParent is "/a/b", then find all
		// nodes which have a full pathname of "/a/b/"
		query.setParameter("name_param", oldFullPathOfParent + "/%");
		
		List<InodeTable> resultList = query.getResultList();

		Transaction tx = session.currentTransaction();
		tx.begin();
		
		for (InodeTable result: resultList) {
			InodeTable newINode = INodeTableHelper.setInodeTableFromInodeTable(result);
			session.deletePersistent(result);
			//result.setName(newFullPathOfParent + result.getName().substring(oldFullPathOfParent.length() - 1));
			String subPath = result.getName().substring(oldFullPathOfParent.length());
			String updatedFullPath = newFullPathOfParent + subPath;
			//System.err.println("[Stateless] new:" + newFullPathOfParent + " subtree:" + sub);
			//System.err.println("[Stateless] concated version: " + (newFullPathOfParent + sub));
			
			newINode.setName(updatedFullPath);
			
			if (updatedFullPath.length() == 1) // root
				newINode.setParent("/");
			else
				newINode.setParent (updatedFullPath.substring(0, updatedFullPath.lastIndexOf("/")));

			session.makePersistent(newINode);
		}
		
		tx.commit();
		session.flush();
	}
	/*FIXME: This method should be removed as soon as InodeTable has
	 * another indexing field, that's not the fullPathname
	 * Probably should be moved to InodeTable class
	 */
	public static InodeTable setInodeTableFromInodeTable( InodeTable inode){
		
		inode.setModificationTime(inode.getModificationTime());
	    inode.setATime(inode.getATime());
	    inode.setLocalName(inode.getLocalName());
	    inode.setPermission(inode.getPermission());
	    inode.setParent(inode.getParent());
	    inode.setNSQuota(inode.getNSQuota());
		inode.setDSQuota(inode.getDSQuota());
		inode.setIsClosedFile(inode.getIsClosedFile());
	   	inode.setIsUnderConstruction(inode.getIsUnderConstruction());
	   	inode.setIsDirWithQuota(inode.getIsDirWithQuota());    
	   	inode.setIsDir(inode.getIsDir());
	   	inode.setIsDirWithQuota(inode.getIsDirWithQuota());    	
    	inode.setNSCount(inode.getNSCount());
    	inode.setDSCount(inode.getDSCount());
	   	inode.setHeader(inode.getHeader());
	   	inode.setSymlink(inode.getSymlink());
		return inode;

	}
	public static void replaceChild (INode thisInode, INode newChild){
		 // [STATELESS]
	      Transaction tx = session.currentTransaction();
	      tx.begin();

	      //InodeTable inode = session.find(InodeTable.class, newChild.getFullPathName());
	      
	      QueryBuilder builder = session.getQueryBuilder();
		  QueryDomainType<InodeTable> domain =
		  builder.createQueryDefinition(InodeTable.class);
		    
		  domain.where(domain.get("name").equal(domain.param(
		  "name")));
		  Query<InodeTable> query = session.createQuery(domain);
		  query.setParameter("name",newChild.getFullPathName());

		  List<InodeTable> results = query.getResultList();
		  assert ! results.isEmpty() : "Child to replace not in DB";
		  InodeTable inode= results.get(0);
	      
	      inode.setModificationTime(thisInode.modificationTime);
	      inode.setATime(thisInode.getAccessTime());
	      inode.setLocalName(thisInode.getLocalName());
	      DataOutputBuffer permissionString = new DataOutputBuffer();
	    
	      try {
	    	  newChild.getPermissionStatus().write(permissionString);
	  	} catch (IOException e) {
	  		// TODO Auto-generated catch block
	  		e.printStackTrace();
	  	}

	     /* long finalPerm = 0;
	      try {
	  		permissionString.writeLong(finalPerm);
	  	} catch (IOException e) {
	  		// TODO Auto-generated catch block
	  		e.printStackTrace();
	  	}*/
	      
	      inode.setPermission(permissionString.getData());
	      inode.setParent(newChild.getParent().getFullPathName());
	      inode.setNSQuota(newChild.getNsQuota());
	      inode.setDSQuota(newChild.getDsQuota());
	      
	      // TODO: Does not handle InodeDirectoryWithQuota yet
	      if (newChild instanceof INodeDirectory)
	      {
	      	inode.setIsDir(true);
	      	inode.setIsDirWithQuota(true);
	      }
	      if (newChild instanceof INodeDirectoryWithQuota)
	      {
	      	inode.setIsDir(false);
	      	inode.setIsDirWithQuota(true);      	
	      	inode.setNSCount(((INodeDirectoryWithQuota) newChild).getNsCount());
	      	inode.setDSCount(((INodeDirectoryWithQuota) newChild).getDsCount());
	      }
	    
	      session.updatePersistent(inode);
	      
	      tx.commit();
	      session.flush();
		
	}
	
	public static INode getChildDirectory(String parentDir, String searchDir) throws IOException {

		/*W: TODO
		 *  1. Get all children of parentDir
            2. search for searchDir in parentDir's children
		 *  3. if found then create an INode and return it
		 *  4. else return null;
		 */

				
		/*
		 * Full table scan
		 * */

		QueryBuilder qb = session.getQueryBuilder();
		QueryDomainType<InodeTable> dobj = qb.createQueryDefinition(InodeTable.class);

		System.err.println("Parent: " + parentDir + " search: " + searchDir);
		dobj.where(dobj.get("parent").equal(dobj.param("parent_param")));

		Query<InodeTable> query = session.createQuery(dobj);
		query.setParameter("parent_param", parentDir); //W: the WHERE clause of SQL

		//Query query = session.createQuery(dobj);
		List<InodeTable> resultList = query.getResultList();


		//TODO: localname needs to be added to the InodeTable to make this work

		for (InodeTable result : resultList) {
			//if(result.getIsDir()) {
				String str = result.getName();
				str = str.substring(str.lastIndexOf("/")+1);
				System.err.println("Comparing " + str + " against " + searchDir);
				if(str.equals(searchDir) ) {
					INode inode = getINodeByNameBasic (result.getName ());

					//System.err.println("[STATELESS] retrieving parent " + result.getParent() + " " + result.getName());
					// Attach a parent to the Inode we just retrieved
					INodeDirectory inodeParent = (INodeDirectory) getINodeByNameBasic(result.getParent());
					System.err.println("[STATELESS] NAME IS: " + inode.getFullPathName());
					inode.setParent(inodeParent);
					return inode;
				}
			//}
		}

		System.out.println("NOT FOUND - " + searchDir + " in "+parentDir);
		return null;
	}

	
	/**
	 * Use this method to retrieve an INode from the
	 * database by its name. This method will not
	 * attach a reference to the parent of the INode
	 * being returned.
	 * 
	 * @param name Inode name to be retrieved
	 * @return INode corresponding to 'name'
	 * @throws IOException 
	 */
	public static INode getINodeByNameBasic (String name) throws IOException{
		
		QueryBuilder qb = session.getQueryBuilder();
		QueryDomainType<InodeTable> dobj = qb.createQueryDefinition(InodeTable.class);


		dobj.where(dobj.get("name").equal(dobj.param("inode_name")));

		Query<InodeTable> query = session.createQuery(dobj);
		query.setParameter("inode_name", name); //W: the WHERE clause of SQL

		List<InodeTable> resultList = query.getResultList();

		assert (resultList.size() == 1) : "More than one Inode exists with name " + name;
				
		for (InodeTable result: resultList) {
			if (result.getName().equals(name))
			{
				return convertINodeTableToINode (result);
			}
		}
		
		// Silence compiler
		return null;
	}
	
	public static INode convertINodeTableToINode (InodeTable inodetable) throws IOException
	{

		DataInputBuffer buffer = new DataInputBuffer();
		buffer.reset(inodetable.getPermission(), inodetable.getPermission().length);
		PermissionStatus ps = PermissionStatus.read(buffer);
		
		INode inode = null;
				
		if (inodetable.getIsDir()){
			inode = new INodeDirectory(inodetable.getName(), ps);
		}
		if (inodetable.getIsDirWithQuota()) {
			inode = new INodeDirectoryWithQuota(inodetable.getName(), ps, inodetable.getNSCount(), inodetable.getDSQuota());
		}
		if (inodetable.getIsUnderConstruction()) {
			/* FIXME: Handle blocks */
			/* FIXME: Double check numbers later */
			BlockInfo [] blocks = new BlockInfo [1];
			blocks[0] = new BlockInfo(3);
			inode = new INodeFileUnderConstruction(inodetable.getName().getBytes(),
													(short) 1,
													inodetable.getModificationTime(),
													64,
													blocks,
													ps,
													inodetable.getClientName(),
													inodetable.getClientMachine(),
													ns.getBlockManager().getDatanodeManager().getDatanodeByHost(inodetable.getClientNode()));
		}
		if (inodetable.getIsClosedFile()) {
			/* FIXME: Double check numbers later */
			inode = new INodeFile(ps,
									0,
									(short)1,
									inodetable.getModificationTime(),
									inodetable.getATime(), 64);	
		}
		
		/* FIXME: Call getLocalName() */
		inode.setFullPathName(inodetable.getName());
		inode.setLocalName(inodetable.getLocalName());
		
		return inode;
	}

}