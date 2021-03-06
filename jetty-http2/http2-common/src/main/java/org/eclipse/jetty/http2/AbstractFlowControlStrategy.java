//
//  ========================================================================
//  Copyright (c) 1995-2015 Mort Bay Consulting Pty. Ltd.
//  ------------------------------------------------------------------------
//  All rights reserved. This program and the accompanying materials
//  are made available under the terms of the Eclipse Public License v1.0
//  and Apache License v2.0 which accompanies this distribution.
//
//      The Eclipse Public License is available at
//      http://www.eclipse.org/legal/epl-v10.html
//
//      The Apache License v2.0 is available at
//      http://www.opensource.org/licenses/apache2.0.php
//
//  You may elect to redistribute this code under either of these licenses.
//  ========================================================================
//

package org.eclipse.jetty.http2;

import org.eclipse.jetty.http2.api.Stream;
import org.eclipse.jetty.http2.frames.WindowUpdateFrame;
import org.eclipse.jetty.util.annotation.ManagedAttribute;
import org.eclipse.jetty.util.annotation.ManagedObject;
import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;

@ManagedObject
public abstract class AbstractFlowControlStrategy implements FlowControlStrategy
{
    protected static final Logger LOG = Log.getLogger(FlowControlStrategy.class);

    private int initialStreamSendWindow;
    private int initialStreamRecvWindow;

    public AbstractFlowControlStrategy(int initialStreamSendWindow)
    {
        this.initialStreamSendWindow = initialStreamSendWindow;
        this.initialStreamRecvWindow = DEFAULT_WINDOW_SIZE;
    }

    @ManagedAttribute(value = "The initial size of stream's flow control send window", readonly = true)
    public int getInitialStreamSendWindow()
    {
        return initialStreamSendWindow;
    }

    @ManagedAttribute(value = "The initial size of stream's flow control receive window", readonly = true)
    public int getInitialStreamRecvWindow()
    {
        return initialStreamRecvWindow;
    }

    @Override
    public void onStreamCreated(IStream stream)
    {
        stream.updateSendWindow(initialStreamSendWindow);
        stream.updateRecvWindow(initialStreamRecvWindow);
    }

    @Override
    public void onStreamDestroyed(IStream stream)
    {
    }

    @Override
    public void updateInitialStreamWindow(ISession session, int initialStreamWindow, boolean local)
    {
        int previousInitialStreamWindow;
        if (local)
        {
            previousInitialStreamWindow = getInitialStreamRecvWindow();
            this.initialStreamRecvWindow = initialStreamWindow;
        }
        else
        {
            previousInitialStreamWindow = getInitialStreamSendWindow();
            this.initialStreamSendWindow = initialStreamWindow;
        }
        int delta = initialStreamWindow - previousInitialStreamWindow;

        // SPEC: updates of the initial window size only affect stream windows, not session's.
        for (Stream stream : session.getStreams())
        {
            if (local)
            {
                ((IStream)stream).updateRecvWindow(delta);
                if (LOG.isDebugEnabled())
                    LOG.debug("Updated initial stream recv window {} -> {} for {}", previousInitialStreamWindow, initialStreamWindow, stream);
            }
            else
            {
                session.onWindowUpdate((IStream)stream, new WindowUpdateFrame(stream.getId(), delta));
            }
        }
    }

    @Override
    public void onWindowUpdate(ISession session, IStream stream, WindowUpdateFrame frame)
    {
        int delta = frame.getWindowDelta();
        if (frame.getStreamId() > 0)
        {
            // The stream may have been removed concurrently.
            if (stream != null)
            {
                int oldSize = stream.updateSendWindow(delta);
                if (LOG.isDebugEnabled())
                    LOG.debug("Updated stream send window {} -> {} for {}", oldSize, oldSize + delta, stream);
                if (oldSize <= 0)
                    onStreamUnstalled(stream);
            }
        }
        else
        {
            int oldSize = session.updateSendWindow(delta);
            if (LOG.isDebugEnabled())
                LOG.debug("Updated session send window {} -> {} for {}", oldSize, oldSize + delta, session);
            if (oldSize <= 0)
                onSessionUnstalled(session);
        }
    }

    @Override
    public void onDataReceived(ISession session, IStream stream, int length)
    {
        int oldSize = session.updateRecvWindow(-length);
        if (LOG.isDebugEnabled())
            LOG.debug("Data received, updated session recv window {} -> {} for {}", oldSize, oldSize - length, session);

        if (stream != null)
        {
            oldSize = stream.updateRecvWindow(-length);
            if (LOG.isDebugEnabled())
                LOG.debug("Data received, updated stream recv window {} -> {} for {}", oldSize, oldSize - length, stream);
        }
    }

    @Override
    public void windowUpdate(ISession session, IStream stream, WindowUpdateFrame frame)
    {
    }

    @Override
    public void onDataSending(IStream stream, int length)
    {
        if (length == 0)
            return;

        ISession session = stream.getSession();
        int oldSize = session.updateSendWindow(-length);
        if (LOG.isDebugEnabled())
            LOG.debug("Updated session send window {} -> {} for {}", oldSize, oldSize - length, session);

        oldSize = stream.updateSendWindow(-length);
        if (LOG.isDebugEnabled())
            LOG.debug("Updated stream send window {} -> {} for {}", oldSize, oldSize - length, stream);
    }

    @Override
    public void onDataSent(IStream stream, int length)
    {
    }

    @Override
    public void onSessionStalled(ISession session)
    {
        if (LOG.isDebugEnabled())
            LOG.debug("Session stalled {}", session);
    }

    @Override
    public void onStreamStalled(IStream stream)
    {
        if (LOG.isDebugEnabled())
            LOG.debug("Stream stalled {}", stream);
    }

    protected void onSessionUnstalled(ISession session)
    {
        if (LOG.isDebugEnabled())
            LOG.debug("Session unstalled {}", session);
    }

    protected void onStreamUnstalled(IStream stream)
    {
        if (LOG.isDebugEnabled())
            LOG.debug("Stream unstalled {}", stream);
    }
}
