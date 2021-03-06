/*
 * Copyright (c) 2016 The Ontario Institute for Cancer Research. All rights reserved.                             
 *                                                                                                               
 * This program and the accompanying materials are made available under the terms of the GNU Public License v3.0.
 * You should have received a copy of the GNU General Public License along with                                  
 * this program. If not, see <http://www.gnu.org/licenses/>.                                                     
 *                                                                                                               
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY                           
 * EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES                          
 * OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT                           
 * SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,                                
 * INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED                          
 * TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS;                               
 * OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER                              
 * IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN                         
 * ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package org.icgc.dcc.download.imports.io;

import static org.icgc.dcc.common.core.util.Formats.formatCount;

import java.io.IOException;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import org.icgc.dcc.dcc.common.es.core.DocumentWriter;
import org.icgc.dcc.dcc.common.es.model.Document;
import org.icgc.dcc.download.imports.service.IndexService;

import com.fasterxml.jackson.databind.node.ObjectNode;

@Slf4j
@RequiredArgsConstructor
public class BaseTarArchiveEntryCallback implements TarArchiveEntryCallback {

  /**
   * Configuration.
   */
  private final boolean applySettings;

  /**
   * Dependencies.
   */
  @NonNull
  private final DocumentWriter documentWriter;
  @NonNull
  private final IndexService indexService;

  /**
   * State.
   */
  private int count = 0;

  @Override
  public void onSettings(ObjectNode settings) {
    if (applySettings) {
      indexService.applySettings(settings);
      indexService.aliasIndex();
    }
  }

  @Override
  public void onMapping(String mappingTypeName, ObjectNode mapping) {
    indexService.applyMapping(mappingTypeName, mapping);
  }

  @Override
  @SneakyThrows
  public void onDocument(Document document) {
    documentWriter.write(document);

    if (++count % 1000 == 0) {
      log.info("Document count: {}", formatCount(count));
    }
  }

  @Override
  public void close() throws IOException {
    documentWriter.close();
  }

}
